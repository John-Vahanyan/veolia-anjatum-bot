package am.veolia.bot.model;

/**
 * A single raw post extracted from the {@code t.me/s/<channel>} HTML preview,
 * before parsing into structured outage fields.
 *
 * @param postId  the value of the post's {@code data-post} attribute, e.g. {@code "VeoliaJur/1234"} —
 *                stable, monotonically increasing per channel, and used to detect duplicates.
 * @param text    the post's plain-text message body.
 */
public record ChannelPost(String postId, String text) {

    /** The numeric suffix of {@code postId} (the part after the slash), used for ordering. */
    public long sequenceNumber() {
        return PostIds.sequenceNumber(postId);
    }
}
