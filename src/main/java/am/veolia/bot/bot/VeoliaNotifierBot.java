package am.veolia.bot.bot;

import am.veolia.bot.config.BotProperties;
import am.veolia.bot.i18n.Messages;
import am.veolia.bot.model.Language;
import am.veolia.bot.model.Subscription;
import am.veolia.bot.model.UserAccount;
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
 * <p>Runs in long-polling mode (no public HTTPS endpoint needed), which keeps
 * deployment to a plain droplet + systemd service trivially simple.
 */
@Component
public class VeoliaNotifierBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(VeoliaNotifierBot.class);

    private static final String LANG_CALLBACK_HY = "lang:HY";
    private static final String LANG_CALLBACK_EN = "lang:EN";
    private static final String UNSUBSCRIBE_CALLBACK_PREFIX = "unsub:";

    /** What we're expecting a chat's *next* plain-text message to mean, if anything. */
    private enum PendingAction {
        AWAITING_SUBSCRIBE_KEYWORD
    }

    private final BotProperties botProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * In-memory only, deliberately not persisted: this just tracks "the user
     * pressed Subscribe and we're waiting for them to type the keyword next."
     * Worst case on a restart mid-flow, the user has to press the button again
     * — not worth a DB column for that.
     */
    private final Map<Long, PendingAction> pendingActions = new ConcurrentHashMap<>();

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

        if (isMenuButton(text, Messages.MENU_SUBSCRIBE_HY, Messages.MENU_SUBSCRIBE_EN)) {
            pendingActions.put(chatId, PendingAction.AWAITING_SUBSCRIBE_KEYWORD);
            send(chatId, Messages.askKeywordToSubscribe(lang));
            return;
        }
        if (isMenuButton(text, Messages.MENU_UNSUBSCRIBE_HY, Messages.MENU_UNSUBSCRIBE_EN)) {
            pendingActions.remove(chatId);
            offerUnsubscribeChoices(chatId, lang);
            return;
        }
        if (isMenuButton(text, Messages.MENU_LIST_HY, Messages.MENU_LIST_EN)) {
            pendingActions.remove(chatId);
            handleList(chatId, lang);
            return;
        }
        if (isMenuButton(text, Messages.MENU_LANGUAGE_HY, Messages.MENU_LANGUAGE_EN)) {
            pendingActions.remove(chatId);
            promptLanguage(chatId);
            return;
        }

        if (text.startsWith("/")) {
            pendingActions.remove(chatId);
            routeCommand(chatId, lang, text);
            return;
        }

        if (pendingActions.remove(chatId) == PendingAction.AWAITING_SUBSCRIBE_KEYWORD) {
            subscribeToKeyword(chatId, lang, text);
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
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(hyButton, enButton))
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

        if (LANG_CALLBACK_HY.equals(data) || LANG_CALLBACK_EN.equals(data)) {
            handleLanguageSelected(chatId, LANG_CALLBACK_EN.equals(data) ? Language.EN : Language.HY);
        } else if (data != null && data.startsWith(UNSUBSCRIBE_CALLBACK_PREFIX)) {
            handleUnsubscribeSelected(chatId, data.substring(UNSUBSCRIBE_CALLBACK_PREFIX.length()));
        }
        answerCallback(callbackQuery.getId());
    }

    private void handleLanguageSelected(long chatId, Language selected) {
        userRepository.setLanguage(chatId, selected);
        send(chatId, Messages.languageUpdated(selected));
        send(chatId, Messages.welcome(selected), buildMainMenu(selected));
    }

    private void handleUnsubscribeSelected(long chatId, String subscriptionIdRaw) {
        Language lang = currentLanguage(chatId);
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
        boolean removed = subscriptionRepository.remove(chatId, keyword);
        if (removed) {
            log.info("chat {} unsubscribed from \"{}\" via menu", chatId, keyword);
        }
        send(chatId, removed ? Messages.unsubscribed(lang, keyword) : Messages.notSubscribed(lang, keyword));
    }

    private void handleSubscribe(long chatId, Language lang, String argument) {
        if (argument.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        subscribeToKeyword(chatId, lang, argument);
    }

    private void subscribeToKeyword(long chatId, Language lang, String rawKeyword) {
        String keyword = rawKeyword.trim();
        if (keyword.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        boolean added = subscriptionRepository.add(chatId, keyword);
        if (added) {
            log.info("chat {} subscribed to \"{}\"", chatId, keyword);
        }
        send(chatId, added ? Messages.subscribed(lang, keyword) : Messages.alreadySubscribed(lang, keyword));
    }

    private void handleUnsubscribe(long chatId, Language lang, String argument) {
        if (argument.isBlank()) {
            send(chatId, Messages.unsubscribeUsage(lang));
            return;
        }
        String keyword = argument.trim();
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
                    .text(subscription.keyword())
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
        List<String> keywords = subscriptionRepository.findKeywordsByUserId(chatId);
        if (keywords.isEmpty()) {
            send(chatId, Messages.listEmpty(lang));
            return;
        }
        StringBuilder sb = new StringBuilder(Messages.listHeader(lang)).append('\n');
        for (String keyword : keywords) {
            sb.append("• ").append(keyword).append('\n');
        }
        send(chatId, sb.toString().stripTrailing());
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

    private boolean isMenuButton(String text, String hyLabel, String enLabel) {
        return text.equals(hyLabel) || text.equals(enLabel);
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
