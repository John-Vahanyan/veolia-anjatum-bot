package am.veolia.bot.repository;

import am.veolia.bot.model.Language;
import am.veolia.bot.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByChatId(long chatId) {
        return jdbcTemplate.query(
                "SELECT chat_id, language FROM users WHERE chat_id = ?",
                (rs, rowNum) -> new UserAccount(rs.getLong("chat_id"), Language.fromCode(rs.getString("language"))),
                chatId
        ).stream().findFirst();
    }

    /** Creates the user with a default language if they don't already exist; leaves existing rows untouched. */
    public void ensureUserExists(long chatId) {
        jdbcTemplate.update(
                "INSERT INTO users (chat_id, language) VALUES (?, ?) ON CONFLICT(chat_id) DO NOTHING",
                chatId, Language.HY.name()
        );
    }

    public void setLanguage(long chatId, Language language) {
        ensureUserExists(chatId);
        jdbcTemplate.update("UPDATE users SET language = ? WHERE chat_id = ?", language.name(), chatId);
    }
}
