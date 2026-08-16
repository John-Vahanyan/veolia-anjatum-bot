package am.veolia.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram bot credentials. {@code token} is deliberately required with no
 * default — the app refuses to start rather than silently running without one.
 */
@ConfigurationProperties(prefix = "app.bot")
public record BotProperties(String token, String username) {

    public BotProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "BOT_TOKEN environment variable is not set. Get a token from @BotFather on "
                            + "Telegram and pass it as BOT_TOKEN. Never hardcode it in source or config.");
        }
    }
}
