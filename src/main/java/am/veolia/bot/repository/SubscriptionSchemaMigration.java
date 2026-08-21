package am.veolia.bot.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time, idempotent migration of the {@code subscriptions} table, run on
 * every startup, that brings an older-shaped table up to the current schema
 * (see schema.sql's comments on the current shape) in whatever number of
 * steps it still needs:
 *
 * <ol>
 *   <li>Pre-scoping databases (no {@code region_code} column at all) get the
 *   table rebuilt from scratch — the standard SQLite migration pattern
 *   (create new table, copy data across, swap names), needed because SQLite
 *   can't retrofit a changed {@code UNIQUE} constraint onto an existing
 *   table via plain DDL. Existing subscriptions come across unscoped
 *   (region/district blank, type {@code KEYWORD}, fuzzy matching allowed —
 *   exactly their old behavior, since we have no record of whether their
 *   keyword was originally typed in Armenian or transliterated).</li>
 *   <li>Databases that already have the scoping columns but not
 *   {@code fuzzy_match} (i.e. already migrated by a previous version of
 *   this class) just get that one column added — no rebuild needed since
 *   the {@code UNIQUE} constraint isn't changing this time.</li>
 * </ol>
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

        if (!columns.contains("region_code")) {
            rebuildForRegionDistrictScoping();
            return; // the rebuilt table already has fuzzy_match too, nothing left to do
        }
        if (!columns.contains("fuzzy_match")) {
            addFuzzyMatchColumn();
        }
    }

    private void rebuildForRegionDistrictScoping() {
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
                    fuzzy_match        INTEGER NOT NULL DEFAULT 1,
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

    private void addFuzzyMatchColumn() {
        log.info("Migrating subscriptions table to add the fuzzy_match column");
        // Existing rows default to 1 (fuzzy allowed) for the same reason as the rebuild path
        // above: we have no record of whether their keyword was typed in Armenian or
        // transliterated, so we preserve the old blanket-fuzzy behavior for them.
        jdbcTemplate.execute("ALTER TABLE subscriptions ADD COLUMN fuzzy_match INTEGER NOT NULL DEFAULT 1");
        log.info("Migration complete");
    }
}
