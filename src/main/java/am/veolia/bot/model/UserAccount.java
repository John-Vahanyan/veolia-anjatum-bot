package am.veolia.bot.model;

/** A bot user, keyed by their private-chat Telegram {@code chat_id}. */
public record UserAccount(long chatId, Language language) {
}
