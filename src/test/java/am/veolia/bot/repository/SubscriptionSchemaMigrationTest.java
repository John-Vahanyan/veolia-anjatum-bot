package am.veolia.bot.repository;

import am.veolia.bot.model.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link SubscriptionSchemaMigration} against a database built with
 * the pre-scoping table shape (no region_code/district_code/subscription_type
 * columns, and the old two-column UNIQUE constraint) — the shape any
 * already-deployed bot instance's database is in before this upgrade.
 */
class SubscriptionSchemaMigrationTest {

    private Path dbFile;

    @AfterEach
    void tearDown() throws IOException {
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void migratesAnOldShapedTableAndPreservesExistingSubscriptions() throws IOException {
        dbFile = Files.createTempFile("veolia-bot-migration-test-", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // The pre-scoping shape: no region_code/district_code/subscription_type,
        // UNIQUE only on (user_id, keyword).
        jdbcTemplate.execute("""
                CREATE TABLE subscriptions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     INTEGER NOT NULL,
                    keyword     TEXT NOT NULL,
                    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
                    UNIQUE (user_id, keyword)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE subscription_events (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER NOT NULL,
                    keyword       TEXT NOT NULL,
                    action        TEXT NOT NULL CHECK (action IN ('SUBSCRIBE', 'UNSUBSCRIBE')),
                    occurred_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )""");
        jdbcTemplate.update("INSERT INTO subscriptions (user_id, keyword) VALUES (?, ?)", 1L, "Աբովյան");
        jdbcTemplate.update("INSERT INTO subscriptions (user_id, keyword) VALUES (?, ?)", 2L, "Կենտրոն");

        new SubscriptionSchemaMigration(jdbcTemplate).run(null);

        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(subscriptions)", (rs, rowNum) -> rs.getString("name"));
        assertThat(columns).contains("region_code", "district_code", "subscription_type");

        SubscriptionRepository repository = new SubscriptionRepository(jdbcTemplate);
        List<Subscription> user1Subscriptions = repository.findByUserId(1L);
        assertThat(user1Subscriptions).hasSize(1);
        Subscription migrated = user1Subscriptions.get(0);
        assertThat(migrated.keyword()).isEqualTo("Աբովյան");
        assertThat(migrated.regionCode()).isEmpty();
        assertThat(migrated.districtCode()).isEmpty();

        // Migrated data plus the new composite UNIQUE constraint must still let a scoped
        // subscription with the *same* keyword text coexist alongside the legacy unscoped one.
        assertThat(repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "KENTRON")).isTrue();
        assertThat(repository.findByUserId(1L)).hasSize(2);
    }

    @Test
    void isANoOpOnAnAlreadyMigratedTable() throws IOException {
        dbFile = Files.createTempFile("veolia-bot-migration-noop-test-", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("""
                CREATE TABLE subscriptions (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id            INTEGER NOT NULL,
                    keyword            TEXT NOT NULL,
                    region_code        TEXT NOT NULL DEFAULT '',
                    district_code      TEXT NOT NULL DEFAULT '',
                    subscription_type  TEXT NOT NULL DEFAULT 'KEYWORD',
                    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
                    UNIQUE (user_id, keyword, region_code, district_code)
                )""");
        jdbcTemplate.update("INSERT INTO subscriptions (user_id, keyword) VALUES (?, ?)", 1L, "Աբովյան");

        new SubscriptionSchemaMigration(jdbcTemplate).run(null);

        assertThat(new SubscriptionRepository(jdbcTemplate).findByUserId(1L)).hasSize(1);
    }
}
