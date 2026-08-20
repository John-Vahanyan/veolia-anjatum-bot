package am.veolia.bot.repository;

import am.veolia.bot.model.Subscription;
import am.veolia.bot.model.SubscriptionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionRepository {

    private static final RowMapper<Subscription> SUBSCRIPTION_ROW_MAPPER = (rs, rowNum) -> new Subscription(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("keyword"),
            rs.getString("region_code"),
            rs.getString("district_code"),
            SubscriptionType.valueOf(rs.getString("subscription_type")),
            rs.getBoolean("fuzzy_match")
    );

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Adds a plain, unscoped subscription (legacy free-text {@code /subscribe <keyword>}).
     * {@code fuzzyMatch} should be true only if {@code keyword} was auto-transliterated from
     * Latin/Cyrillic — see {@link Subscription#fuzzyMatch()}. Returns false if the user was
     * already subscribed to this exact keyword with no scope.
     */
    public boolean add(long userId, String keyword, boolean fuzzyMatch) {
        return add(userId, keyword, "", "", SubscriptionType.KEYWORD, fuzzyMatch);
    }

    /**
     * Adds a region/district-scoped street subscription from the guided flow. {@code fuzzyMatch}
     * should be true only if {@code keyword} was auto-transliterated from Latin/Cyrillic — see
     * {@link Subscription#fuzzyMatch()}. Returns false if the user already had this exact
     * keyword subscribed within this same scope.
     */
    public boolean addScopedStreet(long userId, String keyword, String regionCode, String districtCode, boolean fuzzyMatch) {
        return add(userId, keyword, blankToEmpty(regionCode), blankToEmpty(districtCode), SubscriptionType.SCOPED_STREET, fuzzyMatch);
    }

    /**
     * Adds a "whole region"/"whole district" subscription: unscoped matching (the keyword
     * is the region/district's own name, so it's matched like any other keyword), but the
     * scope columns are still recorded so {@code /list} and the unsubscribe menu can show
     * the user which region/district it refers to. Never fuzzy: the keyword is a canonical
     * name picked via button, not typed, so there's nothing approximate about it.
     */
    public boolean addWholeScope(long userId, String keyword, String regionCode, String districtCode) {
        return add(userId, keyword, blankToEmpty(regionCode), blankToEmpty(districtCode), SubscriptionType.KEYWORD, false);
    }

    /**
     * Uses SQLite's {@code INSERT OR IGNORE} rather than catching a unique-constraint
     * exception, since generic JDBC drivers like sqlite-jdbc aren't guaranteed to
     * translate cleanly into Spring's {@code DuplicateKeyException}.
     */
    private boolean add(long userId, String keyword, String regionCode, String districtCode, SubscriptionType type,
                         boolean fuzzyMatch) {
        int rows = jdbcTemplate.update(
                "INSERT OR IGNORE INTO subscriptions (user_id, keyword, region_code, district_code, subscription_type, fuzzy_match) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                userId, keyword, regionCode, districtCode, type.name(), fuzzyMatch ? 1 : 0
        );
        if (rows > 0) {
            recordEvent(userId, keyword, "SUBSCRIBE");
        }
        return rows > 0;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * True if the user already has a "whole region"/"whole district" ({@link SubscriptionType#KEYWORD})
     * subscription for this exact scope. Used by the guided flow to short-circuit re-offering
     * "whole area"/"enter a street" once that area is already fully covered — a street
     * subscription underneath an existing whole-area one would just double-notify.
     */
    public boolean hasWholeScope(long userId, String regionCode, String districtCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE user_id = ? AND region_code = ? AND district_code = ? "
                        + "AND subscription_type = 'KEYWORD'",
                Integer.class, userId, blankToEmpty(regionCode), blankToEmpty(districtCode)
        );
        return count != null && count > 0;
    }

    /** Returns false if no matching unscoped subscription existed to remove. Used by the typed {@code /unsubscribe <keyword>} command. */
    public boolean remove(long userId, String keyword) {
        int rows = jdbcTemplate.update(
                "DELETE FROM subscriptions WHERE user_id = ? AND keyword = ?",
                userId, keyword
        );
        if (rows > 0) {
            recordEvent(userId, keyword, "UNSUBSCRIBE");
        }
        return rows > 0;
    }

    /** Removes exactly one subscription by id — used by the "tap to unsubscribe" inline menu, which already knows the precise row. */
    public boolean removeById(long userId, long id) {
        Optional<Subscription> subscription = findById(id);
        if (subscription.isEmpty() || subscription.get().userId() != userId) {
            return false;
        }
        int rows = jdbcTemplate.update("DELETE FROM subscriptions WHERE id = ? AND user_id = ?", id, userId);
        if (rows > 0) {
            recordEvent(userId, subscription.get().keyword(), "UNSUBSCRIBE");
        }
        return rows > 0;
    }

    public List<Subscription> findAll() {
        return jdbcTemplate.query(
                "SELECT id, user_id, keyword, region_code, district_code, subscription_type, fuzzy_match FROM subscriptions",
                SUBSCRIPTION_ROW_MAPPER
        );
    }

    /** Used to build the "pick a subscription to remove" inline keyboard, and the /list view. */
    public List<Subscription> findByUserId(long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, keyword, region_code, district_code, subscription_type, fuzzy_match "
                        + "FROM subscriptions WHERE user_id = ? ORDER BY created_at",
                SUBSCRIPTION_ROW_MAPPER,
                userId
        );
    }

    /** Used to resolve which subscription an "unsub:<id>" inline button callback refers to. */
    public Optional<Subscription> findById(long id) {
        return jdbcTemplate.query(
                "SELECT id, user_id, keyword, region_code, district_code, subscription_type, fuzzy_match FROM subscriptions WHERE id = ?",
                SUBSCRIPTION_ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * Appends to the permanent {@code subscription_events} audit trail. Unlike
     * {@code subscriptions} itself, these rows are never deleted — this is what
     * makes "who subscribed/unsubscribed to what, and when" answerable even
     * after a keyword has since been removed.
     */
    private void recordEvent(long userId, String keyword, String action) {
        jdbcTemplate.update(
                "INSERT INTO subscription_events (user_id, keyword, action) VALUES (?, ?, ?)",
                userId, keyword, action
        );
    }
}
