package am.veolia.bot.model;

import java.util.ArrayList;
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
 * @param streets      every street/address fragment the outage affects, across all {@code areas}.
 * @param areas        the same fragments grouped by the area they belong to — one area for an
 *                     ordinary single-district post, several when a post lists e.g.
 *                     "Աջափնյակ վարչական շրջանի՝ ..." and "Կոտայքի մարզի՝ ..." on separate lines.
 * @param rawText      the original, unmodified post text — forwarded to matching subscribers as-is.
 */
public record OutageAnnouncement(
        String postId,
        String district,
        String date,
        String startTime,
        String endTime,
        List<String> streets,
        List<Area> areas,
        String rawText
) {

    /**
     * One area of an announcement and the streets in it.
     *
     * @param scope   text naming the area, matched against a subscription's region/district —
     *                the title clause for a single-area post, or a line's own header
     *                ("Կոտայքի մարզի") in a multi-area one.
     * @param streets the street/address fragments within this area.
     * @param whole   true when the post says the whole area is affected ("... ամբողջությամբ"),
     *                so every street subscription in it should match regardless of street.
     */
    public record Area(String scope, List<String> streets, boolean whole) {
    }

    /** A single-area announcement scoped to the title's {@code district} — the common case. */
    public OutageAnnouncement(String postId, String district, String date, String startTime, String endTime,
                              List<String> streets, String rawText) {
        this(postId, district, date, startTime, endTime, streets,
                List.of(new Area(district, streets, false)), rawText);
    }

    /**
     * All text fields a user keyword may match against: every street/address fragment, every
     * area's scope, and the title district.
     */
    public List<String> matchableFragments() {
        List<String> fragments = new ArrayList<>(streets);
        for (Area area : areas) {
            if (area.scope() != null && !area.scope().isBlank() && !fragments.contains(area.scope())) {
                fragments.add(area.scope());
            }
        }
        if (district != null && !district.isBlank() && !fragments.contains(district)) {
            fragments.add(district);
        }
        return fragments;
    }
}
