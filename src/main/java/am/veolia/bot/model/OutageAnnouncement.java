package am.veolia.bot.model;

import java.util.List;

/**
 * A structured, parsed version of a "Վթարային ջրանջատում" (emergency water
 * outage) channel post.
 *
 * @param postId       the source channel post id, e.g. {@code "VeoliaJur/1234"}.
 * @param district     the city/district clause from the title line, e.g. {@code "Երևանի Շենգավիթ վարչական շրջանում"}.
 * @param date         the raw date fragment as written in the post, e.g. {@code "օգոստոսի 16"}.
 * @param startTime    outage start time, e.g. {@code "14:00"}.
 * @param endTime      outage end time, e.g. {@code "16:00"}; {@code null} when the post only gives a start time.
 * @param streets      the individual street/address fragments the outage affects.
 * @param rawText      the original, unmodified post text — forwarded to matching subscribers as-is.
 */
public record OutageAnnouncement(
        String postId,
        String district,
        String date,
        String startTime,
        String endTime,
        List<String> streets,
        String rawText
) {

    /**
     * All text fields a user keyword may match against: district plus every street/address fragment.
     */
    public List<String> matchableFragments() {
        List<String> fragments = new java.util.ArrayList<>(streets);
        if (district != null && !district.isBlank()) {
            fragments.add(district);
        }
        return fragments;
    }
}
