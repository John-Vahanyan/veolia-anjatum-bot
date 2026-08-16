package am.veolia.bot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tracks which channel posts have already been processed, keyed by the
 * channel's own {@code post_id} (e.g. {@code "VeoliaJur/1234"}). This is what
 * makes polling idempotent across restarts: on every cycle each fetched post
 * is checked against this table before it's parsed/matched, so a crash or
 * redeploy can never reprocess an already-notified post or silently drop one
 * that arrived during downtime (it's simply picked up on the next poll).
 */
@Repository
public class ProcessedPostRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedPostRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isProcessed(String postId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_posts WHERE post_id = ?", Integer.class, postId);
        return count != null && count > 0;
    }

    public void markProcessed(String postId) {
        jdbcTemplate.update("INSERT OR IGNORE INTO processed_posts (post_id) VALUES (?)", postId);
    }
}
