package am.veolia.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The public Telegram channel to poll, e.g. {@code VeoliaJur} for
 * {@code https://t.me/s/VeoliaJur}. Configurable per deployment via
 * {@code CHANNEL_USERNAME} so this project isn't hardwired to one channel.
 */
@ConfigurationProperties(prefix = "app.channel")
public record ChannelProperties(String username) {

    public ChannelProperties {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("app.channel.username (CHANNEL_USERNAME) must not be blank");
        }
        username = username.trim();
    }

    /** Public, login-free HTML preview URL Telegram exposes for any public channel. */
    public String previewUrl() {
        return "https://t.me/s/" + username;
    }
}
