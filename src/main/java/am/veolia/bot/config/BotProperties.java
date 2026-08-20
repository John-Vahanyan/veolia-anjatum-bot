package am.veolia.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram bot credentials. {@code token} is deliberately required with no
 * default — the app refuses to start rather than silently running without one.
 *
 * @param adminChatId numeric Telegram chat id to notify whenever someone subscribes, or
 *                     {@code null}/blank to disable that notification entirely. Optional,
 *                     unlike {@code token} — the bot works fine without it.
 */
@ConfigurationProperties(prefix = "app.bot")
public record BotProperties(String token, String username, String adminChatId) {

    public BotProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "BOT_TOKEN environment variable is not set. Get a token from @BotFather on "
                            + "Telegram and pass it as BOT_TOKEN. Never hardcode it in source or config.");
        }
    }
}
