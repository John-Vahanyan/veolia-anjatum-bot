package am.veolia.bot.model;

/** Helpers for Telegram's {@code data-post="channel/1234"} post id format. */
public final class PostIds {

    private PostIds() {
    }

    /** The numeric suffix of a post id (the part after the slash), used for chronological ordering. */
    public static long sequenceNumber(String postId) {
        int slash = postId.lastIndexOf('/');
        String numeric = slash >= 0 ? postId.substring(slash + 1) : postId;
        try {
            return Long.parseLong(numeric);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
