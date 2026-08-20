package am.veolia.bot.bot;

import am.veolia.bot.config.BotProperties;
import am.veolia.bot.i18n.Messages;
import am.veolia.bot.model.District;
import am.veolia.bot.model.Language;
import am.veolia.bot.model.Region;
import am.veolia.bot.model.Subscription;
import am.veolia.bot.model.SubscriptionType;
import am.veolia.bot.model.UserAccount;
import am.veolia.bot.parser.Transliterator;
import am.veolia.bot.repository.SubscriptionRepository;
import am.veolia.bot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The user-facing side of the bot: {@code /start}, {@code /language},
 * {@code /subscribe}, {@code /unsubscribe}, {@code /list}, {@code /menu},
 * {@code /help} — plus a persistent button menu that mirrors those same
 * actions for users who'd rather tap than type.
 *
 * <p>Subscribing is a guided, button-driven flow: pick a region, then (for
 * Yerevan) a district, then choose either "the whole area" or "a specific
 * street" — see {@link #sendRegionPicker}. Narrowing to a region/district
 * before typing a street name is what lets matching require the
 * announcement to actually be in that area (see {@code KeywordMatcher
 * #matchesScoped}), instead of a bare street name fuzzy-matching a
 * similarly-spelled street anywhere in the country.
 *
 * <p>Runs in long-polling mode (no public HTTPS endpoint needed), which keeps
 * deployment to a plain droplet + systemd service trivially simple.
 */
@Component
public class VeoliaNotifierBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(VeoliaNotifierBot.class);

    private static final String LANG_CALLBACK_HY = "lang:HY";
    private static final String LANG_CALLBACK_EN = "lang:EN";
    private static final String LANG_CALLBACK_RU = "lang:RU";
    private static final String UNSUBSCRIBE_CALLBACK_PREFIX = "unsub:";

    // Guided-subscribe callback prefixes. None of these is a string-prefix of
    // another (the ":" lands at a different index in each), so a simple
    // startsWith chain in handleCallbackQuery can't misroute between them.
    private static final String REGION_CALLBACK_PREFIX = "subr:";
    private static final String REGION_ALL_CALLBACK_PREFIX = "subra:";
    private static final String REGION_STREET_CALLBACK_PREFIX = "subrs:";
    private static final String DISTRICT_CALLBACK_PREFIX = "subd:";
    private static final String DISTRICT_ALL_CALLBACK_PREFIX = "subda:";
    private static final String DISTRICT_STREET_CALLBACK_PREFIX = "subds:";

    /**
     * What we're expecting a chat's *next* plain-text message to mean: the
     * street name for a subscription already scoped to a region and/or
     * district. Either code may be blank but not both (a bare region scope
     * leaves districtCode blank; a Yerevan district scope always carries
     * {@link Region#YEREVAN}'s code alongside its own).
     */
    private record PendingStreetEntry(String regionCode, String districtCode) {
    }

    private final BotProperties botProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * In-memory only, deliberately not persisted: this just tracks "the user
     * picked a region/district and we're waiting for them to type the street
     * name next." Worst case on a restart mid-flow, the user has to start
     * over — not worth a DB column for that.
     */
    private final Map<Long, PendingStreetEntry> pendingActions = new ConcurrentHashMap<>();

    public VeoliaNotifierBot(BotProperties botProperties,
                              UserRepository userRepository,
                              SubscriptionRepository subscriptionRepository) {
        super(botProperties.token());
        this.botProperties = botProperties;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public String getBotUsername() {
        String username = botProperties.username();
        return username == null || username.isBlank() ? "veolia_jur_notifier_bot" : username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            // Never let a single malformed update take the long-polling loop down.
            log.error("Error handling update {}: {}", update.getUpdateId(), e.toString(), e);
        }
    }

    private void handleMessage(Message message) {
        long chatId = message.getChatId();
        String text = message.getText().trim();
        userRepository.ensureUserExists(chatId);
        Language lang = currentLanguage(chatId);

        if (isMenuButton(text, Messages.MENU_SUBSCRIBE_HY, Messages.MENU_SUBSCRIBE_EN, Messages.MENU_SUBSCRIBE_RU)) {
            pendingActions.remove(chatId);
            sendRegionPicker(chatId, lang);
            return;
        }
        if (isMenuButton(text, Messages.MENU_UNSUBSCRIBE_HY, Messages.MENU_UNSUBSCRIBE_EN, Messages.MENU_UNSUBSCRIBE_RU)) {
            pendingActions.remove(chatId);
            offerUnsubscribeChoices(chatId, lang);
            return;
        }
        if (isMenuButton(text, Messages.MENU_LIST_HY, Messages.MENU_LIST_EN, Messages.MENU_LIST_RU)) {
            pendingActions.remove(chatId);
            handleList(chatId, lang);
            return;
        }
        if (isMenuButton(text, Messages.MENU_LANGUAGE_HY, Messages.MENU_LANGUAGE_EN, Messages.MENU_LANGUAGE_RU)) {
            pendingActions.remove(chatId);
            promptLanguage(chatId);
            return;
        }

        if (text.startsWith("/")) {
            pendingActions.remove(chatId);
            routeCommand(chatId, lang, text);
            return;
        }

        PendingStreetEntry pending = pendingActions.remove(chatId);
        if (pending != null) {
            subscribeToScopedStreet(chatId, lang, text, pending.regionCode(), pending.districtCode());
            return;
        }

        send(chatId, Messages.unknownCommand(lang));
    }

    private void routeCommand(long chatId, Language lang, String text) {
        String command;
        String argument;
        int spaceIdx = text.indexOf(' ');
        if (spaceIdx >= 0) {
            command = text.substring(0, spaceIdx);
            argument = text.substring(spaceIdx + 1).trim();
        } else {
            command = text;
            argument = "";
        }
        // Telegram commands can be suffixed with @BotUsername in group chats; strip it.
        int atIdx = command.indexOf('@');
        if (atIdx >= 0) {
            command = command.substring(0, atIdx);
        }

        switch (command.toLowerCase(Locale.ROOT)) {
            case "/start" -> handleStart(chatId);
            case "/menu" -> send(chatId, Messages.menuTitle(lang), buildMainMenu(lang));
            case "/language" -> promptLanguage(chatId);
            case "/subscribe" -> handleSubscribe(chatId, lang, argument);
            case "/unsubscribe" -> handleUnsubscribe(chatId, lang, argument);
            case "/list" -> handleList(chatId, lang);
            case "/help" -> send(chatId, Messages.help(lang));
            default -> send(chatId, Messages.unknownCommand(lang));
        }
    }

    private void handleStart(long chatId) {
        promptLanguage(chatId);
    }

    private void promptLanguage(long chatId) {
        InlineKeyboardButton hyButton = InlineKeyboardButton.builder()
                .text("Հայերեն").callbackData(LANG_CALLBACK_HY).build();
        InlineKeyboardButton enButton = InlineKeyboardButton.builder()
                .text("English").callbackData(LANG_CALLBACK_EN).build();
        InlineKeyboardButton ruButton = InlineKeyboardButton.builder()
                .text("Русский").callbackData(LANG_CALLBACK_RU).build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(hyButton, enButton, ruButton))
                .build();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(Messages.chooseLanguagePrompt())
                .replyMarkup(markup)
                .build();
        execute(sendMessage);
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        Language lang = currentLanguage(chatId);

        if (LANG_CALLBACK_HY.equals(data) || LANG_CALLBACK_EN.equals(data) || LANG_CALLBACK_RU.equals(data)) {
            Language selected = switch (data) {
                case LANG_CALLBACK_EN -> Language.EN;
                case LANG_CALLBACK_RU -> Language.RU;
                default -> Language.HY;
            };
            handleLanguageSelected(chatId, selected);
        } else if (data != null && data.startsWith(UNSUBSCRIBE_CALLBACK_PREFIX)) {
            handleUnsubscribeSelected(chatId, lang, data.substring(UNSUBSCRIBE_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(REGION_ALL_CALLBACK_PREFIX)) {
            handleWholeRegionPicked(chatId, lang, data.substring(REGION_ALL_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(REGION_STREET_CALLBACK_PREFIX)) {
            handleRegionStreetPicked(chatId, lang, data.substring(REGION_STREET_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(REGION_CALLBACK_PREFIX)) {
            handleRegionPicked(chatId, lang, data.substring(REGION_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(DISTRICT_ALL_CALLBACK_PREFIX)) {
            handleWholeDistrictPicked(chatId, lang, data.substring(DISTRICT_ALL_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(DISTRICT_STREET_CALLBACK_PREFIX)) {
            handleDistrictStreetPicked(chatId, lang, data.substring(DISTRICT_STREET_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(DISTRICT_CALLBACK_PREFIX)) {
            handleDistrictPicked(chatId, lang, data.substring(DISTRICT_CALLBACK_PREFIX.length()));
        }
        answerCallback(callbackQuery.getId());
    }

    private void handleLanguageSelected(long chatId, Language selected) {
        userRepository.setLanguage(chatId, selected);
        send(chatId, Messages.languageUpdated(selected));
        send(chatId, Messages.welcome(selected), buildMainMenu(selected));
    }

    // ---- Guided subscribe flow ------------------------------------------------

    private void sendRegionPicker(long chatId, Language lang) {
        send(chatId, Messages.chooseRegionPrompt(lang), buildRegionKeyboard(lang));
    }

    private void sendDistrictPicker(long chatId, Language lang) {
        send(chatId, Messages.chooseDistrictPrompt(lang), buildDistrictKeyboard(lang));
    }

    private void handleRegionPicked(long chatId, Language lang, String regionCode) {
        Region region = Region.fromCode(regionCode);
        if (region == null) {
            return;
        }
        if (region == Region.YEREVAN) {
            sendDistrictPicker(chatId, lang);
            return;
        }
        InlineKeyboardMarkup markup = buildScopeChoiceKeyboard(lang, false,
                REGION_ALL_CALLBACK_PREFIX + region.name(),
                REGION_STREET_CALLBACK_PREFIX + region.name());
        send(chatId, Messages.scopeChosenPrompt(lang, region.displayName(lang)), markup);
    }

    private void handleWholeRegionPicked(long chatId, Language lang, String regionCode) {
        Region region = Region.fromCode(regionCode);
        if (region == null) {
            return;
        }
        pendingActions.remove(chatId);
        String display = region.displayName(lang);
        boolean added = subscriptionRepository.addWholeScope(chatId, region.armenian(), region.name(), "");
        if (added) {
            log.info("chat {} subscribed to whole region {}", chatId, region.name());
        }
        send(chatId, added ? Messages.subscribedWholeScope(lang, display) : Messages.alreadySubscribedWholeScope(lang, display));
    }

    private void handleRegionStreetPicked(long chatId, Language lang, String regionCode) {
        Region region = Region.fromCode(regionCode);
        if (region == null) {
            return;
        }
        pendingActions.put(chatId, new PendingStreetEntry(region.name(), ""));
        send(chatId, Messages.askStreetNameInScope(lang, region.displayName(lang)));
    }

    private void handleDistrictPicked(long chatId, Language lang, String districtCode) {
        District district = District.fromCode(districtCode);
        if (district == null) {
            return;
        }
        InlineKeyboardMarkup markup = buildScopeChoiceKeyboard(lang, true,
                DISTRICT_ALL_CALLBACK_PREFIX + district.name(),
                DISTRICT_STREET_CALLBACK_PREFIX + district.name());
        send(chatId, Messages.scopeChosenPrompt(lang, district.displayName(lang)), markup);
    }

    private void handleWholeDistrictPicked(long chatId, Language lang, String districtCode) {
        District district = District.fromCode(districtCode);
        if (district == null) {
            return;
        }
        pendingActions.remove(chatId);
        String display = district.displayName(lang);
        boolean added = subscriptionRepository.addWholeScope(chatId, district.armenian(), Region.YEREVAN.name(), district.name());
        if (added) {
            log.info("chat {} subscribed to whole district {}", chatId, district.name());
        }
        send(chatId, added ? Messages.subscribedWholeScope(lang, display) : Messages.alreadySubscribedWholeScope(lang, display));
    }

    private void handleDistrictStreetPicked(long chatId, Language lang, String districtCode) {
        District district = District.fromCode(districtCode);
        if (district == null) {
            return;
        }
        pendingActions.put(chatId, new PendingStreetEntry(Region.YEREVAN.name(), district.name()));
        send(chatId, Messages.askStreetNameInScope(lang, district.displayName(lang)));
    }

    private InlineKeyboardMarkup buildRegionKeyboard(Language lang) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Region region : Region.values()) {
            row.add(InlineKeyboardButton.builder()
                    .text(region.displayName(lang))
                    .callbackData(REGION_CALLBACK_PREFIX + region.name())
                    .build());
            if (row.size() == 2) {
                builder.keyboardRow(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            builder.keyboardRow(List.copyOf(row));
        }
        return builder.build();
    }

    private InlineKeyboardMarkup buildDistrictKeyboard(Language lang) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (District district : District.values()) {
            row.add(InlineKeyboardButton.builder()
                    .text(district.displayName(lang))
                    .callbackData(DISTRICT_CALLBACK_PREFIX + district.name())
                    .build());
            if (row.size() == 2) {
                builder.keyboardRow(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            builder.keyboardRow(List.copyOf(row));
        }
        return builder.build();
    }

    private InlineKeyboardMarkup buildScopeChoiceKeyboard(Language lang, boolean isDistrict,
                                                            String wholeCallback, String streetCallback) {
        InlineKeyboardButton wholeButton = InlineKeyboardButton.builder()
                .text(isDistrict ? Messages.buttonWholeDistrict(lang) : Messages.buttonWholeRegion(lang))
                .callbackData(wholeCallback)
                .build();
        InlineKeyboardButton streetButton = InlineKeyboardButton.builder()
                .text(Messages.buttonEnterStreet(lang))
                .callbackData(streetCallback)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(wholeButton))
                .keyboardRow(List.of(streetButton))
                .build();
    }

    // ---- Subscribe / unsubscribe ------------------------------------------------

    private void handleUnsubscribeSelected(long chatId, Language lang, String subscriptionIdRaw) {
        long subscriptionId;
        try {
            subscriptionId = Long.parseLong(subscriptionIdRaw);
        } catch (NumberFormatException e) {
            return;
        }

        Optional<Subscription> subscription = subscriptionRepository.findById(subscriptionId);
        // Ignore stale/foreign buttons (e.g. a re-shown old keyboard) rather than acting on them.
        if (subscription.isEmpty() || subscription.get().userId() != chatId) {
            return;
        }

        String keyword = subscription.get().keyword();
        boolean removed = subscriptionRepository.removeById(chatId, subscriptionId);
        if (removed) {
            log.info("chat {} unsubscribed from \"{}\" via menu", chatId, keyword);
        }
        send(chatId, removed ? Messages.unsubscribed(lang, keyword) : Messages.notSubscribed(lang, keyword));
    }

    /** Legacy typed-command path: {@code /subscribe} with no argument now launches the guided flow instead. */
    private void handleSubscribe(long chatId, Language lang, String argument) {
        if (argument.isBlank()) {
            pendingActions.remove(chatId);
            sendRegionPicker(chatId, lang);
            return;
        }
        subscribeToKeyword(chatId, lang, argument);
    }

    /** Legacy typed-command path: an unscoped keyword, matched against every fragment of an announcement. */
    private void subscribeToKeyword(long chatId, Language lang, String rawKeyword) {
        String typed = rawKeyword.trim();
        if (typed.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        // Veolia Jur only ever posts in Armenian, so a Latin/Russian-lettered keyword
        // would otherwise never match anything — convert it before storing/matching.
        String keyword = Transliterator.toArmenianBestEffort(typed);
        boolean wasTransliterated = !keyword.equals(typed);

        boolean added = subscriptionRepository.add(chatId, keyword);
        if (added) {
            log.info("chat {} subscribed to \"{}\"{}", chatId, keyword,
                    wasTransliterated ? " (transliterated from \"" + typed + "\")" : "");
        }

        if (!added) {
            send(chatId, Messages.alreadySubscribed(lang, keyword));
        } else if (wasTransliterated) {
            send(chatId, Messages.subscribedTransliterated(lang, typed, keyword));
        } else {
            send(chatId, Messages.subscribed(lang, keyword));
        }
    }

    /** Guided-flow path: a street name entered after narrowing to a region/district scope. */
    private void subscribeToScopedStreet(long chatId, Language lang, String rawKeyword, String regionCode, String districtCode) {
        String typed = rawKeyword.trim();
        String scopeDisplay = scopeDisplayName(lang, regionCode, districtCode);
        if (typed.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        String keyword = Transliterator.toArmenianBestEffort(typed);
        boolean wasTransliterated = !keyword.equals(typed);

        boolean added = subscriptionRepository.addScopedStreet(chatId, keyword, regionCode, districtCode);
        if (added) {
            log.info("chat {} subscribed to \"{}\" scoped to region={} district={}{}",
                    chatId, keyword, regionCode, districtCode,
                    wasTransliterated ? " (transliterated from \"" + typed + "\")" : "");
        }

        if (!added) {
            send(chatId, Messages.alreadySubscribedScopedStreet(lang, scopeDisplay, keyword));
        } else if (wasTransliterated) {
            send(chatId, Messages.subscribedScopedStreetTransliterated(lang, scopeDisplay, typed, keyword));
        } else {
            send(chatId, Messages.subscribedScopedStreet(lang, scopeDisplay, keyword));
        }
    }

    private String scopeDisplayName(Language lang, String regionCode, String districtCode) {
        District district = District.fromCode(districtCode);
        if (district != null) {
            return district.displayName(lang);
        }
        Region region = Region.fromCode(regionCode);
        return region != null ? region.displayName(lang) : "";
    }

    private void handleUnsubscribe(long chatId, Language lang, String argument) {
        if (argument.isBlank()) {
            send(chatId, Messages.unsubscribeUsage(lang));
            return;
        }
        // Subscriptions are stored in their transliterated Armenian form (see
        // subscribeToKeyword), so a lookup by the raw typed text needs the same
        // conversion or it will never find the matching row.
        String keyword = Transliterator.toArmenianBestEffort(argument.trim());
        boolean removed = subscriptionRepository.remove(chatId, keyword);
        if (removed) {
            log.info("chat {} unsubscribed from \"{}\"", chatId, keyword);
        }
        send(chatId, removed ? Messages.unsubscribed(lang, keyword) : Messages.notSubscribed(lang, keyword));
    }

    /** Shows one inline button per current subscription; tapping one unsubscribes from it. */
    private void offerUnsubscribeChoices(long chatId, Language lang) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserId(chatId);
        if (subscriptions.isEmpty()) {
            send(chatId, Messages.listEmpty(lang));
            return;
        }

        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder markupBuilder = InlineKeyboardMarkup.builder();
        for (Subscription subscription : subscriptions) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(describeSubscription(subscription, lang))
                    .callbackData(UNSUBSCRIBE_CALLBACK_PREFIX + subscription.id())
                    .build();
            markupBuilder.keyboardRow(List.of(button));
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(Messages.chooseKeywordToUnsubscribe(lang))
                .replyMarkup(markupBuilder.build())
                .build();
        execute(sendMessage);
    }

    private void handleList(long chatId, Language lang) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserId(chatId);
        if (subscriptions.isEmpty()) {
            send(chatId, Messages.listEmpty(lang));
            return;
        }
        StringBuilder sb = new StringBuilder(Messages.listHeader(lang)).append('\n');
        for (Subscription subscription : subscriptions) {
            sb.append("• ").append(describeSubscription(subscription, lang)).append('\n');
        }
        send(chatId, sb.toString().stripTrailing());
    }

    /**
     * Human-readable label for a subscription, showing every scope level so a Yerevan street
     * subscription reads "Yerevan — Kentron — Abovyan", a whole-district one reads
     * "Yerevan — Kentron — All", a region street one reads "Kotayk — Abovyan", a whole-region
     * one reads "Kotayk — All", and a legacy unscoped subscription (no recorded scope) falls
     * back to just the bare keyword.
     */
    private String describeSubscription(Subscription subscription, Language lang) {
        District district = subscription.district();
        Region region = subscription.region();
        String area = subscription.type() == SubscriptionType.SCOPED_STREET
                ? subscription.keyword()
                : Messages.allLabel(lang);

        if (district != null) {
            return Region.YEREVAN.displayName(lang) + " — " + district.displayName(lang) + " — " + area;
        }
        if (region != null) {
            return region.displayName(lang) + " — " + area;
        }
        return subscription.keyword();
    }

    /** The always-visible bottom keyboard: one full-width button per row, Telegram's classic style. */
    private ReplyKeyboardMarkup buildMainMenu(Language lang) {
        KeyboardRow subscribeRow = new KeyboardRow();
        subscribeRow.add(new KeyboardButton(Messages.menuSubscribeLabel(lang)));
        KeyboardRow unsubscribeRow = new KeyboardRow();
        unsubscribeRow.add(new KeyboardButton(Messages.menuUnsubscribeLabel(lang)));
        KeyboardRow listRow = new KeyboardRow();
        listRow.add(new KeyboardButton(Messages.menuListLabel(lang)));
        KeyboardRow languageRow = new KeyboardRow();
        languageRow.add(new KeyboardButton(Messages.menuLanguageLabel(lang)));

        return ReplyKeyboardMarkup.builder()
                .keyboardRow(subscribeRow)
                .keyboardRow(unsubscribeRow)
                .keyboardRow(listRow)
                .keyboardRow(languageRow)
                .resizeKeyboard(true)
                .build();
    }

    private boolean isMenuButton(String text, String... labels) {
        for (String label : labels) {
            if (text.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private Language currentLanguage(long chatId) {
        return userRepository.findByChatId(chatId).map(UserAccount::language).orElse(Language.HY);
    }

    /** Sends a plain text message, logging (not throwing) on delivery failure — e.g. a user blocked the bot. */
    public void send(long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId).text(text).build());
    }

    private void send(long chatId, String text, ReplyKeyboard replyMarkup) {
        execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(replyMarkup).build());
    }

    private void execute(SendMessage sendMessage) {
        try {
            super.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.warn("Failed to send message to chat {}: {}", sendMessage.getChatId(), e.toString());
        }
    }

    private void answerCallback(String callbackQueryId) {
        try {
            execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Failed to answer callback query {}: {}", callbackQueryId, e.toString());
        }
    }
}
