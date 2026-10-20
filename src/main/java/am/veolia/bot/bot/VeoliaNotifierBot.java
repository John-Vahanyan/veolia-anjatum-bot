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
import org.telegram.telegrambots.meta.api.methods.description.SetMyDescription;
import org.telegram.telegrambots.meta.api.methods.description.SetMyShortDescription;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The user-facing side of the bot: {@code /start}, {@code /language},
 * {@code /subscribe}, {@code /unsubscribe}, {@code /list}, {@code /menu},
 * {@code /support}, {@code /help} — plus a persistent button menu that mirrors
 * those same actions for users who'd rather tap than type.
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
    private static final String BACK_TO_REGIONS_CALLBACK = "subback:region";
    private static final String BACK_TO_DISTRICTS_CALLBACK = "subback:district";

    // Admin-only: replying to a forwarded support message. "sup" (not "sub") so these can't
    // collide with the guided-subscribe prefixes above.
    private static final String SUPPORT_REPLY_CALLBACK_PREFIX = "supreply:";
    private static final String SUPPORT_SEND_CALLBACK_PREFIX = "supsend:";
    private static final String SUPPORT_CANCEL_CALLBACK_PREFIX = "supcancel:";

    /** What we're expecting a chat's *next* plain-text message to mean, if anything. */
    private sealed interface PendingAction permits PendingStreetEntry, PendingSupportMessage, PendingAdminReply {
    }

    /**
     * The street name for a subscription already scoped to a region and/or
     * district. Either code may be blank but not both (a bare region scope
     * leaves districtCode blank; a Yerevan district scope always carries
     * {@link Region#YEREVAN}'s code alongside its own).
     */
    private record PendingStreetEntry(String regionCode, String districtCode) implements PendingAction {
    }

    /** A bug report or suggestion typed after /support — see {@link #handleSupportMessage}. */
    private record PendingSupportMessage() implements PendingAction {
    }

    private static final PendingSupportMessage PENDING_SUPPORT = new PendingSupportMessage();

    /** The admin tapped "Reply" under a support message; their next text is the answer to {@code targetChatId}. */
    private record PendingAdminReply(long targetChatId) implements PendingAction {
    }

    /** An admin reply typed and awaiting the "Send" / "Cancel" confirmation — see {@link #handleAdminReplyText}. */
    private record AdminReplyDraft(long targetChatId, String targetDescription, String text) {
    }

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    // A whole token containing a digit ("12", "12/3", "12ա", "N5"), a standalone "№" sign, or a
    // "building"/"house" word that only makes sense next to a number — including the transliterated
    // forms of Russian "д."/"дом" ("դ."/"դոմ"), since the keyword is already Armenianized here.
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile(
            "\\S*\\d\\S*|№|(?<!\\S)(?:շենք|շ|տուն|տ|դոմ|դ)\\.?(?!\\S)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WHITESPACE_RUN_PATTERN = Pattern.compile("\\s+");
    // Separators left dangling at either end once the numbers are gone ("Աբովյան , " -> "Աբովյան").
    private static final Pattern EDGE_JUNK_PATTERN = Pattern.compile("^[\\s,.;:/-]+|[\\s,.;:/-]+$");

    /** Telegram rejects messages over 4096 characters; this leaves room for the header we prepend. */
    private static final int MAX_SUPPORT_MESSAGE_LENGTH = 3500;

    private final BotProperties botProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /** Numeric chat id to notify on every new subscription, or {@code null} if disabled — see {@link #notifyAdminOfSubscription}. */
    private final Long adminChatId;

    /**
     * In-memory only, deliberately not persisted: this just tracks "the user
     * picked a region/district and we're waiting for them to type the street
     * name next." Worst case on a restart mid-flow, the user has to start
     * over — not worth a DB column for that.
     */
    private final Map<Long, PendingAction> pendingActions = new ConcurrentHashMap<>();

    /**
     * Admin replies awaiting confirmation, keyed by a draft id carried in the confirm buttons'
     * callback data (the text itself is too long to fit there). Kept apart from
     * {@link #pendingActions} because any button tap clears that, including the confirm tap
     * itself. Removed on send/cancel, so a second tap on the same button can't send twice.
     * In-memory like pendingActions: a restart just means re-tapping "Reply".
     */
    private final Map<Long, AdminReplyDraft> adminReplyDrafts = new ConcurrentHashMap<>();
    private final AtomicLong nextAdminReplyDraftId = new AtomicLong();

    public VeoliaNotifierBot(BotProperties botProperties,
                              UserRepository userRepository,
                              SubscriptionRepository subscriptionRepository) {
        super(botProperties.token());
        this.botProperties = botProperties;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.adminChatId = parseAdminChatId(botProperties.adminChatId());
    }

    private static Long parseAdminChatId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("app.bot.admin-chat-id (\"{}\") is not a valid numeric chat id; "
                    + "admin subscribe notifications are disabled", raw);
            return null;
        }
    }

    @Override
    public String getBotUsername() {
        String username = botProperties.username();
        return username == null || username.isBlank() ? "veolia_jur_notifier_bot" : username;
    }

    /**
     * Sets the profile texts Telegram shows before the user presses Start and on the bot's
     * profile, so people can tell up front that this isn't Veolia Jur's own bot. Armenian is
     * the default (no language code); English and Russian override it for users whose Telegram
     * app is in that language. Failures are only logged — a stale description isn't worth
     * refusing to start over.
     */
    public void publishProfileDescriptions() {
        publishProfileDescription(null, Language.HY);
        publishProfileDescription("en", Language.EN);
        publishProfileDescription("ru", Language.RU);
    }

    private void publishProfileDescription(String languageCode, Language lang) {
        try {
            execute(SetMyDescription.builder()
                    .description(Messages.botDescription(lang))
                    .languageCode(languageCode)
                    .build());
            execute(SetMyShortDescription.builder()
                    .shortDescription(Messages.botShortDescription(lang))
                    .languageCode(languageCode)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to set bot profile description for language {}: {}", lang, e.toString());
        }
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
        if (isMenuButton(text, Messages.MENU_SUPPORT_HY, Messages.MENU_SUPPORT_EN, Messages.MENU_SUPPORT_RU)) {
            promptSupport(chatId, lang);
            return;
        }

        if (text.startsWith("/")) {
            pendingActions.remove(chatId);
            routeCommand(chatId, lang, text, message.getFrom());
            return;
        }

        PendingAction pending = pendingActions.remove(chatId);
        if (pending instanceof PendingStreetEntry street) {
            subscribeToScopedStreet(chatId, lang, text, street.regionCode(), street.districtCode(), message.getFrom());
            return;
        }
        if (pending instanceof PendingSupportMessage) {
            handleSupportMessage(chatId, lang, text, message.getFrom());
            return;
        }
        if (pending instanceof PendingAdminReply reply && isAdmin(chatId)) {
            handleAdminReplyText(chatId, reply.targetChatId(), text);
            return;
        }

        send(chatId, Messages.unknownCommand(lang));
    }

    private void routeCommand(long chatId, Language lang, String text, User from) {
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
            case "/subscribe" -> handleSubscribe(chatId, lang, argument, from);
            case "/unsubscribe" -> handleUnsubscribe(chatId, lang, argument);
            case "/list" -> handleList(chatId, lang);
            case "/support" -> handleSupport(chatId, lang, argument, from);
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

        // Any inline button tap means the user didn't type the street name we may have been
        // waiting for — clear it up front so a *later* stray text message can never be
        // misread as confirming a subscription from an abandoned flow. The two handlers that
        // actually ask for a street name (below) re-arm this right after.
        pendingActions.remove(chatId);

        if (LANG_CALLBACK_HY.equals(data) || LANG_CALLBACK_EN.equals(data) || LANG_CALLBACK_RU.equals(data)) {
            Language selected = switch (data) {
                case LANG_CALLBACK_EN -> Language.EN;
                case LANG_CALLBACK_RU -> Language.RU;
                default -> Language.HY;
            };
            handleLanguageSelected(chatId, selected);
        } else if (data != null && data.startsWith(UNSUBSCRIBE_CALLBACK_PREFIX)) {
            handleUnsubscribeSelected(chatId, lang, data.substring(UNSUBSCRIBE_CALLBACK_PREFIX.length()));
        } else if (BACK_TO_REGIONS_CALLBACK.equals(data)) {
            sendRegionPicker(chatId, lang);
        } else if (BACK_TO_DISTRICTS_CALLBACK.equals(data)) {
            sendDistrictPicker(chatId, lang);
        } else if (data != null && data.startsWith(REGION_ALL_CALLBACK_PREFIX)) {
            handleWholeRegionPicked(chatId, lang, data.substring(REGION_ALL_CALLBACK_PREFIX.length()), callbackQuery.getFrom());
        } else if (data != null && data.startsWith(REGION_STREET_CALLBACK_PREFIX)) {
            handleRegionStreetPicked(chatId, lang, data.substring(REGION_STREET_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(REGION_CALLBACK_PREFIX)) {
            handleRegionPicked(chatId, lang, data.substring(REGION_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(DISTRICT_ALL_CALLBACK_PREFIX)) {
            handleWholeDistrictPicked(chatId, lang, data.substring(DISTRICT_ALL_CALLBACK_PREFIX.length()), callbackQuery.getFrom());
        } else if (data != null && data.startsWith(DISTRICT_STREET_CALLBACK_PREFIX)) {
            handleDistrictStreetPicked(chatId, lang, data.substring(DISTRICT_STREET_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(DISTRICT_CALLBACK_PREFIX)) {
            handleDistrictPicked(chatId, lang, data.substring(DISTRICT_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(SUPPORT_REPLY_CALLBACK_PREFIX) && isAdmin(chatId)) {
            handleAdminReplyPicked(chatId, data.substring(SUPPORT_REPLY_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(SUPPORT_SEND_CALLBACK_PREFIX) && isAdmin(chatId)) {
            handleAdminReplyConfirmed(chatId, data.substring(SUPPORT_SEND_CALLBACK_PREFIX.length()));
        } else if (data != null && data.startsWith(SUPPORT_CANCEL_CALLBACK_PREFIX) && isAdmin(chatId)) {
            handleAdminReplyCancelled(chatId, data.substring(SUPPORT_CANCEL_CALLBACK_PREFIX.length()));
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
        // Already covers every street in this region — offering "whole region" again is a
        // no-op, and offering "enter a street" would just create a redundant subscription
        // (the whole-region one already matches it, so the user would be notified twice for
        // the same announcement). Tell them plainly instead of re-showing the same choice.
        if (subscriptionRepository.hasWholeScope(chatId, region.name(), "")) {
            sendAlreadySubscribedWholeScope(chatId, lang, region.displayName(lang), BACK_TO_REGIONS_CALLBACK);
            return;
        }
        InlineKeyboardMarkup markup = buildScopeChoiceKeyboard(lang, false,
                REGION_ALL_CALLBACK_PREFIX + region.name(),
                REGION_STREET_CALLBACK_PREFIX + region.name());
        send(chatId, Messages.scopeChosenPrompt(lang, region.displayName(lang)), markup);
    }

    private void handleWholeRegionPicked(long chatId, Language lang, String regionCode, User from) {
        Region region = Region.fromCode(regionCode);
        if (region == null) {
            return;
        }
        String display = region.displayName(lang);
        boolean added = subscriptionRepository.addWholeScope(chatId, region.armenian(), region.name(), "");
        if (added) {
            log.info("chat {} subscribed to whole region {}", chatId, region.name());
            notifyAdminOfSubscription(from, display + " — " + Messages.allLabel(Language.EN));
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
        // Same reasoning as the region case above: don't re-offer a choice that either does
        // nothing (whole district again) or creates a redundant, double-notifying subscription
        // (a street already covered by the existing whole-district one).
        if (subscriptionRepository.hasWholeScope(chatId, Region.YEREVAN.name(), district.name())) {
            sendAlreadySubscribedWholeScope(chatId, lang, district.displayName(lang), BACK_TO_DISTRICTS_CALLBACK);
            return;
        }
        InlineKeyboardMarkup markup = buildScopeChoiceKeyboard(lang, true,
                DISTRICT_ALL_CALLBACK_PREFIX + district.name(),
                DISTRICT_STREET_CALLBACK_PREFIX + district.name());
        send(chatId, Messages.scopeChosenPrompt(lang, district.displayName(lang)), markup);
    }

    private void handleWholeDistrictPicked(long chatId, Language lang, String districtCode, User from) {
        District district = District.fromCode(districtCode);
        if (district == null) {
            return;
        }
        String display = district.displayName(lang);
        boolean added = subscriptionRepository.addWholeScope(chatId, district.armenian(), Region.YEREVAN.name(), district.name());
        if (added) {
            log.info("chat {} subscribed to whole district {}", chatId, district.name());
            notifyAdminOfSubscription(from,
                    Region.YEREVAN.displayName(Language.EN) + " — " + display + " — " + Messages.allLabel(Language.EN));
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
        builder.keyboardRow(List.of(backToRegionsButton(lang)));
        return builder.build();
    }

    private InlineKeyboardButton backToRegionsButton(Language lang) {
        return InlineKeyboardButton.builder()
                .text(Messages.buttonGoBack(lang))
                .callbackData(BACK_TO_REGIONS_CALLBACK)
                .build();
    }

    /** Tells the user they already have a "whole area" subscription for this scope, with a single button back to the picker they came from. */
    private void sendAlreadySubscribedWholeScope(long chatId, Language lang, String scopeDisplayName, String backCallback) {
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text(Messages.buttonGoBack(lang))
                .callbackData(backCallback)
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(backButton))
                .build();
        send(chatId, Messages.alreadySubscribedWholeScope(lang, scopeDisplayName), markup);
    }

    /**
     * The "whole area" / "enter a street" choice, plus a back button. Per how the guided flow
     * is wired, "back" from here always returns to the region list (not the district list, for
     * the Yerevan case) — one consistent way out of either 2-button screen.
     */
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
                .keyboardRow(List.of(backToRegionsButton(lang)))
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
    private void handleSubscribe(long chatId, Language lang, String argument, User from) {
        if (argument.isBlank()) {
            pendingActions.remove(chatId);
            sendRegionPicker(chatId, lang);
            return;
        }
        subscribeToKeyword(chatId, lang, argument, from);
    }

    /** Legacy typed-command path: an unscoped keyword, matched against every fragment of an announcement. */
    private void subscribeToKeyword(long chatId, Language lang, String rawKeyword, User from) {
        String typed = rawKeyword.trim();
        if (typed.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        // Veolia Jur only ever posts in Armenian, so a Latin/Russian-lettered keyword
        // would otherwise never match anything — convert it before storing/matching.
        String keyword = Transliterator.toArmenianBestEffort(typed);
        boolean wasTransliterated = !keyword.equals(typed);

        boolean added = subscriptionRepository.add(chatId, keyword, wasTransliterated);
        if (added) {
            log.info("chat {} subscribed to \"{}\"{}", chatId, keyword,
                    wasTransliterated ? " (transliterated from \"" + typed + "\")" : "");
            notifyAdminOfSubscription(from, keyword);
        }

        if (!added) {
            send(chatId, Messages.alreadySubscribed(lang, keyword));
        } else if (wasTransliterated) {
            send(chatId, Messages.subscribedTransliterated(lang, typed, keyword));
        } else {
            send(chatId, Messages.subscribed(lang, keyword));
        }
        sendStreetNumberTipIfNeeded(chatId, lang, keyword);
    }

    /** Guided-flow path: a street name entered after narrowing to a region/district scope. */
    private void subscribeToScopedStreet(long chatId, Language lang, String rawKeyword, String regionCode,
                                          String districtCode, User from) {
        String typed = rawKeyword.trim();
        String scopeDisplay = scopeDisplayName(lang, regionCode, districtCode);
        if (typed.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        String keyword = Transliterator.toArmenianBestEffort(typed);
        boolean wasTransliterated = !keyword.equals(typed);

        boolean added = subscriptionRepository.addScopedStreet(chatId, keyword, regionCode, districtCode, wasTransliterated);
        if (added) {
            log.info("chat {} subscribed to \"{}\" scoped to region={} district={}{}",
                    chatId, keyword, regionCode, districtCode,
                    wasTransliterated ? " (transliterated from \"" + typed + "\")" : "");
            notifyAdminOfSubscription(from, scopeDisplay + " — " + keyword);
        }

        if (!added) {
            send(chatId, Messages.alreadySubscribedScopedStreet(lang, scopeDisplay, keyword));
        } else if (wasTransliterated) {
            send(chatId, Messages.subscribedScopedStreetTransliterated(lang, scopeDisplay, typed, keyword));
        } else {
            send(chatId, Messages.subscribedScopedStreet(lang, scopeDisplay, keyword));
        }
        sendStreetNumberTipIfNeeded(chatId, lang, keyword);
    }

    /**
     * Posts list a street once followed by its building numbers ("Աբովյան 12, 14, 16"), so a
     * keyword with a number in it ("Աբովյան 12") easily misses an announcement for that very
     * building. The subscription is still kept as typed — the user may know better — but they
     * get a follow-up suggesting the bare street name instead.
     */
    private void sendStreetNumberTipIfNeeded(long chatId, Language lang, String keyword) {
        if (!DIGIT_PATTERN.matcher(keyword).find()) {
            return;
        }
        String withoutNumbers = NUMBER_TOKEN_PATTERN.matcher(keyword).replaceAll(" ");
        String suggestion = EDGE_JUNK_PATTERN.matcher(WHITESPACE_RUN_PATTERN.matcher(withoutNumbers).replaceAll(" "))
                .replaceAll("");
        send(chatId, Messages.streetNumberTip(lang, suggestion));
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

    // ---- Support / feedback ------------------------------------------------

    /**
     * {@code /support}. With no argument this arms {@link PendingSupportMessage} and asks the user
     * to write; a message typed inline as {@code /support <text>} is taken as the report itself, so
     * a user who already knows what they want to say doesn't need the extra round trip.
     */
    private void handleSupport(long chatId, Language lang, String argument, User from) {
        if (argument.isBlank()) {
            promptSupport(chatId, lang);
            return;
        }
        handleSupportMessage(chatId, lang, argument, from);
    }

    private void promptSupport(long chatId, Language lang) {
        pendingActions.put(chatId, PENDING_SUPPORT);
        send(chatId, Messages.supportPrompt(lang));
    }

    /** Forwards a bug report or suggestion to {@link #adminChatId} and thanks the user. */
    private void handleSupportMessage(long chatId, Language lang, String text, User from) {
        String feedback = text.trim();
        if (feedback.isBlank()) {
            send(chatId, Messages.supportEmpty(lang));
            return;
        }
        if (feedback.length() > MAX_SUPPORT_MESSAGE_LENGTH) {
            feedback = feedback.substring(0, MAX_SUPPORT_MESSAGE_LENGTH) + "…";
        }
        // Logged unconditionally: with no admin chat configured, the log is the only
        // place the user's message survives at all.
        log.info("support message from chat {} ({}): {}", chatId, describeUser(from), feedback);
        if (adminChatId != null) {
            InlineKeyboardButton replyButton = InlineKeyboardButton.builder()
                    .text("↩️ Reply")
                    .callbackData(SUPPORT_REPLY_CALLBACK_PREFIX + chatId)
                    .build();
            send(adminChatId, "💬 Support message from " + describeUser(from) + ":\n\n" + feedback,
                    InlineKeyboardMarkup.builder().keyboardRow(List.of(replyButton)).build());
        } else {
            log.warn("no app.bot.admin-chat-id configured — the support message above was not delivered to anyone");
        }
        send(chatId, Messages.supportReceived(lang));
    }

    // ---- Admin replies to support messages ------------------------------------

    private boolean isAdmin(long chatId) {
        return adminChatId != null && adminChatId == chatId;
    }

    /** The admin tapped "Reply" under a forwarded support message: wait for the answer text. */
    private void handleAdminReplyPicked(long adminChat, String targetChatIdRaw) {
        long targetChatId;
        try {
            targetChatId = Long.parseLong(targetChatIdRaw);
        } catch (NumberFormatException e) {
            return;
        }
        pendingActions.put(adminChat, new PendingAdminReply(targetChatId));
        send(adminChat, "✍️ Write your reply to " + describeChat(targetChatId)
                + ". You'll be asked to confirm before it's sent.\n\nTo cancel, use any menu button or command.");
    }

    /** The admin typed their answer: show it back with "Send" / "Cancel" instead of sending right away. */
    private void handleAdminReplyText(long adminChat, long targetChatId, String text) {
        if (text.length() > MAX_SUPPORT_MESSAGE_LENGTH) {
            pendingActions.put(adminChat, new PendingAdminReply(targetChatId));
            send(adminChat, "That reply is too long (" + text.length() + " characters, max "
                    + MAX_SUPPORT_MESSAGE_LENGTH + "). Please send a shorter one.");
            return;
        }
        String targetDescription = describeChat(targetChatId);
        long draftId = nextAdminReplyDraftId.incrementAndGet();
        adminReplyDrafts.put(draftId, new AdminReplyDraft(targetChatId, targetDescription, text));

        InlineKeyboardButton sendButton = InlineKeyboardButton.builder()
                .text("✅ Yes, send")
                .callbackData(SUPPORT_SEND_CALLBACK_PREFIX + draftId)
                .build();
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("❌ Cancel")
                .callbackData(SUPPORT_CANCEL_CALLBACK_PREFIX + draftId)
                .build();
        send(adminChat, "Do you really want to send this to " + targetDescription + "?\n\n" + text,
                InlineKeyboardMarkup.builder().keyboardRow(List.of(sendButton, cancelButton)).build());
    }

    private void handleAdminReplyConfirmed(long adminChat, String draftIdRaw) {
        AdminReplyDraft draft = takeAdminReplyDraft(adminChat, draftIdRaw);
        if (draft == null) {
            return;
        }
        Language targetLang = currentLanguage(draft.targetChatId());
        boolean delivered = trySend(draft.targetChatId(), Messages.supportReplyHeader(targetLang) + "\n\n" + draft.text());
        log.info("admin reply to chat {} ({}){}: {}", draft.targetChatId(), draft.targetDescription(),
                delivered ? "" : " FAILED", draft.text());
        send(adminChat, delivered
                ? "✅ Sent to " + draft.targetDescription() + "."
                : "⚠️ Couldn't deliver to " + draft.targetDescription() + " — they may have blocked the bot.");
    }

    private void handleAdminReplyCancelled(long adminChat, String draftIdRaw) {
        if (takeAdminReplyDraft(adminChat, draftIdRaw) != null) {
            send(adminChat, "Cancelled — nothing was sent.");
        }
    }

    /** Removes and returns the draft, or tells the admin it's gone (already sent/cancelled, or lost to a restart). */
    private AdminReplyDraft takeAdminReplyDraft(long adminChat, String draftIdRaw) {
        AdminReplyDraft draft = null;
        try {
            draft = adminReplyDrafts.remove(Long.parseLong(draftIdRaw));
        } catch (NumberFormatException e) {
            // fall through to the "gone" message
        }
        if (draft == null) {
            send(adminChat, "This reply was already sent or cancelled.");
        }
        return draft;
    }

    /** Like {@link #describeUser}, but for a chat we only know the id of — falls back to just the id. */
    private String describeChat(long chatId) {
        try {
            Chat chat = super.execute(GetChat.builder().chatId(chatId).build());
            String name = String.join(" ",
                            Stream.of(chat.getFirstName(), chat.getLastName())
                                    .filter(part -> part != null && !part.isBlank())
                                    .toList())
                    .trim();
            if (name.isBlank()) {
                name = "id " + chatId;
            }
            String username = chat.getUserName();
            return username != null && !username.isBlank()
                    ? name + " (@" + username + ", id " + chatId + ")"
                    : name + " (id " + chatId + ")";
        } catch (TelegramApiException e) {
            log.debug("Failed to look up chat {}: {}", chatId, e.toString());
            return "id " + chatId;
        }
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
        KeyboardRow supportRow = new KeyboardRow();
        supportRow.add(new KeyboardButton(Messages.menuSupportLabel(lang)));

        return ReplyKeyboardMarkup.builder()
                .keyboardRow(subscribeRow)
                .keyboardRow(unsubscribeRow)
                .keyboardRow(listRow)
                .keyboardRow(languageRow)
                .keyboardRow(supportRow)
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

    /**
     * Notifies {@link #adminChatId} (if configured) that {@code subscriber} just added
     * {@code subscriptionDescription}. A no-op — cheaply, before doing any formatting — when
     * no admin chat id is configured, so this is safe to call unconditionally on every
     * successful subscribe.
     */
    private void notifyAdminOfSubscription(User subscriber, String subscriptionDescription) {
        if (adminChatId == null) {
            return;
        }
        send(adminChatId, "🔔 " + describeUser(subscriber) + " subscribed to: " + subscriptionDescription);
    }

    /** "First Last (@username, id 123)" — as much of that as Telegram actually gave us. */
    private static String describeUser(User user) {
        if (user == null) {
            return "unknown user";
        }
        String name = String.join(" ",
                        Stream.of(user.getFirstName(), user.getLastName())
                                .filter(part -> part != null && !part.isBlank())
                                .toList())
                .trim();
        if (name.isBlank()) {
            name = "id " + user.getId();
        }
        String username = user.getUserName();
        return username != null && !username.isBlank()
                ? name + " (@" + username + ", id " + user.getId() + ")"
                : name + " (id " + user.getId() + ")";
    }

    /** Sends a plain text message, logging (not throwing) on delivery failure — e.g. a user blocked the bot. */
    public void send(long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId).text(text).build());
    }

    private void send(long chatId, String text, ReplyKeyboard replyMarkup) {
        execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(replyMarkup).build());
    }

    private void execute(SendMessage sendMessage) {
        trySend(sendMessage);
    }

    /** Like {@link #send(long, String)}, but reports whether Telegram accepted the message. */
    private boolean trySend(long chatId, String text) {
        return trySend(SendMessage.builder().chatId(chatId).text(text).build());
    }

    private boolean trySend(SendMessage sendMessage) {
        try {
            super.execute(sendMessage);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Failed to send message to chat {}: {}", sendMessage.getChatId(), e.toString());
            return false;
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
