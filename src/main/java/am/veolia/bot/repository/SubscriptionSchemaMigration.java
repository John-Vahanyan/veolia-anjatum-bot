package am.veolia.bot.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time, idempotent migration of the {@code subscriptions} table for
 * databases created before region/district scoping was added (see
 * schema.sql's comment on the current shape).
 *
 * <p>{@code schema.sql}'s {@code CREATE TABLE IF NOT EXISTS} runs on every
 * startup but can't retrofit new columns or a changed {@code UNIQUE}
 * constraint onto an already-existing SQLite table, so this rebuilds the
 * table from scratch the first time it finds the old shape — the standard
 * SQLite migration pattern (create new table, copy data across, swap
 * names). Existing subscriptions come across unscoped (region/district
 * blank, type {@code KEYWORD}), which is exactly their old behavior:
 * matched against every fragment of an announcement, no narrower geography
 * to fall back to.
 *
 * <p>Runs as an {@link ApplicationRunner}, which Spring Boot guarantees
 * fires only after {@code schema.sql} has already created the table (fresh
 * databases) or left an older-shaped one in place (migrated here).
 */
@Component
public class SubscriptionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(subscriptions)", (rs, rowNum) -> rs.getString("name"));
        if (columns.contains("region_code")) {
            return; // already migrated, or created fresh by schema.sql
        }

        log.info("Migrating subscriptions table to add region/district scoping columns");
        jdbcTemplate.execute("ALTER TABLE subscriptions RENAME TO subscriptions_old");
        jdbcTemplate.execute("""
                CREATE TABLE subscriptions (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id            INTEGER NOT NULL REFERENCES users (chat_id),
                    keyword            TEXT NOT NULL,
                    region_code        TEXT NOT NULL DEFAULT '',
                    district_code      TEXT NOT NULL DEFAULT '',
                    subscription_type  TEXT NOT NULL DEFAULT 'KEYWORD' CHECK (subscription_type IN ('KEYWORD', 'SCOPED_STREET')),
                    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
                    UNIQUE (user_id, keyword, region_code, district_code)
                )""");
        jdbcTemplate.execute("""
                INSERT INTO subscriptions (id, user_id, keyword, created_at)
                SELECT id, user_id, keyword, created_at FROM subscriptions_old""");
        jdbcTemplate.execute("DROP TABLE subscriptions_old");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id)");
        log.info("Migration complete");
    }
}
