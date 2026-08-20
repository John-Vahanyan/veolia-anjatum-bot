package am.veolia.bot.repository;

import am.veolia.bot.model.Subscription;
import am.veolia.bot.model.SubscriptionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SubscriptionRepository} against a real (temp-file) SQLite
 * database using the actual {@code schema.sql}, rather than mocking JDBC —
 * the region/district scoping columns and the composite UNIQUE constraint
 * are exactly the kind of thing that looks right in isolation but breaks
 * against a real driver.
 */
class SubscriptionRepositoryTest {

    private Path dbFile;
    private JdbcTemplate jdbcTemplate;
    private SubscriptionRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("veolia-bot-test-", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : schemaStatements()) {
            jdbcTemplate.execute(statement);
        }
        repository = new SubscriptionRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    void addAndFindRoundTripAnUnscopedKeyword() {
        assertThat(repository.add(1L, "Աբովյան", false)).isTrue();

        List<Subscription> found = repository.findByUserId(1L);
        assertThat(found).hasSize(1);
        Subscription subscription = found.get(0);
        assertThat(subscription.keyword()).isEqualTo("Աբովյան");
        assertThat(subscription.regionCode()).isEmpty();
        assertThat(subscription.districtCode()).isEmpty();
        assertThat(subscription.type()).isEqualTo(SubscriptionType.KEYWORD);
        assertThat(subscription.fuzzyMatch()).isFalse();
    }

    @Test
    void addStoresTheFuzzyMatchFlagAsGiven() {
        assertThat(repository.add(1L, "Komitas-derived", true)).isTrue();

        assertThat(repository.findByUserId(1L).get(0).fuzzyMatch()).isTrue();
    }

    @Test
    void addingTheSameUnscopedKeywordTwiceIsRejected() {
        assertThat(repository.add(1L, "Աբովյան", false)).isTrue();
        assertThat(repository.add(1L, "Աբովյան", false)).isFalse();
        assertThat(repository.findByUserId(1L)).hasSize(1);
    }

    @Test
    void theSameStreetKeywordCanBeSubscribedInTwoDifferentDistricts() {
        // The whole point of scoping: identical street text, different area, both kept.
        assertThat(repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "KENTRON", false)).isTrue();
        assertThat(repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "SHENGAVIT", false)).isTrue();

        List<Subscription> found = repository.findByUserId(1L);
        assertThat(found).hasSize(2);
        assertThat(found).extracting(Subscription::districtCode).containsExactlyInAnyOrder("KENTRON", "SHENGAVIT");
        assertThat(found).allMatch(s -> s.type() == SubscriptionType.SCOPED_STREET);
    }

    @Test
    void addingTheSameScopedStreetTwiceIsRejected() {
        assertThat(repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "KENTRON", false)).isTrue();
        assertThat(repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "KENTRON", false)).isFalse();
        assertThat(repository.findByUserId(1L)).hasSize(1);
    }

    @Test
    void addWholeScopeStoresTheRegionOrDistrictCodeWithKeywordTypeAndNoFuzzyTolerance() {
        assertThat(repository.addWholeScope(1L, "Կոտայք", "KOTAYK", "")).isTrue();

        Subscription subscription = repository.findByUserId(1L).get(0);
        assertThat(subscription.type()).isEqualTo(SubscriptionType.KEYWORD);
        assertThat(subscription.regionCode()).isEqualTo("KOTAYK");
        assertThat(subscription.districtCode()).isEmpty();
        assertThat(subscription.fuzzyMatch()).isFalse();
    }

    @Test
    void hasWholeScopeIsTrueOnlyAfterAWholeRegionSubscriptionExists() {
        assertThat(repository.hasWholeScope(1L, "KOTAYK", "")).isFalse();

        repository.addWholeScope(1L, "Կոտայք", "KOTAYK", "");

        assertThat(repository.hasWholeScope(1L, "KOTAYK", "")).isTrue();
    }

    @Test
    void hasWholeScopeIsTrueOnlyAfterAWholeDistrictSubscriptionExists() {
        assertThat(repository.hasWholeScope(1L, "YEREVAN", "KENTRON")).isFalse();

        repository.addWholeScope(1L, "Կենտրոն", "YEREVAN", "KENTRON");

        assertThat(repository.hasWholeScope(1L, "YEREVAN", "KENTRON")).isTrue();
    }

    @Test
    void hasWholeScopeIgnoresAScopedStreetSubscriptionInTheSameArea() {
        // A specific street subscription within a region/district must not be mistaken for
        // a "whole area" one -- they're deliberately different SubscriptionTypes.
        repository.addScopedStreet(1L, "Աբովյան", "KOTAYK", "", false);

        assertThat(repository.hasWholeScope(1L, "KOTAYK", "")).isFalse();
    }

    @Test
    void removeByIdDeletesOnlyTheTargetedRowEvenWithASharedKeyword() {
        repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "KENTRON", false);
        repository.addScopedStreet(1L, "Աբովյան", "YEREVAN", "SHENGAVIT", false);
        long keepId = repository.findByUserId(1L).stream()
                .filter(s -> s.districtCode().equals("SHENGAVIT"))
                .findFirst().orElseThrow().id();
        long removeId = repository.findByUserId(1L).stream()
                .filter(s -> s.districtCode().equals("KENTRON"))
                .findFirst().orElseThrow().id();

        assertThat(repository.removeById(1L, removeId)).isTrue();

        List<Subscription> remaining = repository.findByUserId(1L);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).id()).isEqualTo(keepId);
    }

    @Test
    void removeByIdRefusesToDeleteAnotherUsersSubscription() {
        repository.add(1L, "Աբովյան", false);
        long id = repository.findByUserId(1L).get(0).id();

        assertThat(repository.removeById(2L, id)).isFalse();
        assertThat(repository.findByUserId(1L)).hasSize(1);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(repository.findById(999L)).isEqualTo(Optional.empty());
    }

    private static List<String> schemaStatements() {
        return List.of(
                """
                CREATE TABLE users (
                    chat_id     INTEGER PRIMARY KEY,
                    language    TEXT NOT NULL DEFAULT 'HY',
                    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
                )""",
                """
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
                )""",
                """
                CREATE TABLE subscription_events (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER NOT NULL,
                    keyword       TEXT NOT NULL,
                    action        TEXT NOT NULL CHECK (action IN ('SUBSCRIBE', 'UNSUBSCRIBE')),
                    occurred_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )"""
        );
    }
}
