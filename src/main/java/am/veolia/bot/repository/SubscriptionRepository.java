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
        return rows > 0;
    }

    /** Returns false if no matching subscription existed to remove. */
    public boolean remove(long userId, String keyword) {
        int rows = jdbcTemplate.update(
                "DELETE FROM subscriptions WHERE user_id = ? AND keyword = ?",
                userId, keyword
        );
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
}
