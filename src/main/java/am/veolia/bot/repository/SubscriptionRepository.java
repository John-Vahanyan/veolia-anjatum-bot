package am.veolia.bot.repository;

import am.veolia.bot.model.Subscription;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SubscriptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns false if the user was already subscribed to this exact keyword.
     * Uses SQLite's {@code INSERT OR IGNORE} rather than catching a unique-constraint
     * exception, since generic JDBC drivers like sqlite-jdbc aren't guaranteed to
     * translate cleanly into Spring's {@code DuplicateKeyException}.
     */
    public boolean add(long userId, String keyword) {
        int rows = jdbcTemplate.update(
                "INSERT OR IGNORE INTO subscriptions (user_id, keyword) VALUES (?, ?)",
                userId, keyword
        );
        if (rows > 0) {
            recordEvent(userId, keyword, "SUBSCRIBE");
        }
        return rows > 0;
    }

    /** Returns false if no matching subscription existed to remove. */
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

    public List<String> findKeywordsByUserId(long userId) {
        return jdbcTemplate.queryForList(
                "SELECT keyword FROM subscriptions WHERE user_id = ? ORDER BY created_at",
                String.class, userId
        );
    }

    public List<Subscription> findAll() {
        return jdbcTemplate.query(
                "SELECT id, user_id, keyword FROM subscriptions",
                (rs, rowNum) -> new Subscription(rs.getLong("id"), rs.getLong("user_id"), rs.getString("keyword"))
        );
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
