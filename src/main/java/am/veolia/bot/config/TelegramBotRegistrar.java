package am.veolia.bot.config;

import am.veolia.bot.bot.VeoliaNotifierBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Registers {@link VeoliaNotifierBot} with the Telegram Bot API in long-polling
 * mode once the Spring context is fully up.
 */
@Component
public class TelegramBotRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);

    private final VeoliaNotifierBot bot;

    public TelegramBotRegistrar(VeoliaNotifierBot bot) {
        this.bot = bot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerBot() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(bot);
            log.info("Telegram bot registered and long-polling started");
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Failed to register Telegram bot", e);
        }
    }
}
