package am.veolia.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Path to the SQLite database file. Must point outside the build/output
 * directory in production (see README "Deployment") so that rebuilding and
 * restarting the systemd service never discards subscriber data.
 */
@ConfigurationProperties(prefix = "app.db")
public record DbProperties(String path) {

    public DbProperties {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("app.db.path (BOT_DB_PATH) must not be blank");
        }
    }
}
