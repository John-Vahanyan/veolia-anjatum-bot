package am.veolia.bot.bot;

import am.veolia.bot.config.BotProperties;
import am.veolia.bot.i18n.Messages;
import am.veolia.bot.model.Language;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Locale;

/**
 * The user-facing side of the bot: {@code /start}, {@code /language},
 * {@code /subscribe}, {@code /unsubscribe}, {@code /list}, {@code /help}.
 *
 * <p>Runs in long-polling mode (no public HTTPS endpoint needed), which keeps
 * deployment to a plain droplet + systemd service trivially simple.
 */
@Component
public class VeoliaNotifierBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(VeoliaNotifierBot.class);

    private static final String LANG_CALLBACK_HY = "lang:HY";
    private static final String LANG_CALLBACK_EN = "lang:EN";

    private final BotProperties botProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

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
        Language selected = switch (data) {
            case LANG_CALLBACK_EN -> Language.EN;
            default -> Language.HY;
        };
        userRepository.setLanguage(chatId, selected);
        send(chatId, Messages.languageUpdated(selected));
        send(chatId, Messages.welcome(selected));
        answerCallback(callbackQuery.getId());
    }

    private void handleSubscribe(long chatId, Language lang, String argument) {
        if (argument.isBlank()) {
            send(chatId, Messages.subscribeUsage(lang));
            return;
        }
        String keyword = argument.trim();
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

    private Language currentLanguage(long chatId) {
        return userRepository.findByChatId(chatId).map(UserAccount::language).orElse(Language.HY);
    }

    /** Sends a plain text message, logging (not throwing) on delivery failure — e.g. a user blocked the bot. */
    public void send(long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId).text(text).build());
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
