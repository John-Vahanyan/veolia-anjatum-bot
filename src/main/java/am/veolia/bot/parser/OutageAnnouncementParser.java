package am.veolia.bot.parser;

import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.District;
import am.veolia.bot.model.OutageAnnouncement;
import am.veolia.bot.model.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses "Վթարային ջրանջատում" (emergency water outage) posts from the
 * Veolia Jur channel into structured {@link OutageAnnouncement}s.
 *
 * <p>A typical post has a title line naming the district/city and date,
 * followed by a body sentence of the shape:
 * {@code "... ժամը <start>-<end>-ն կդադարեցվի <streets> ջրամատակարարումը:"}.
 * Real-world posts vary in punctuation and wording, so this parser is
 * deliberately permissive about whitespace/line breaks and defensive about
 * anything it can't confidently extract: it never throws on malformed input,
 * it logs a warning and returns {@link Optional#empty()} so the poller can
 * skip the post rather than crash.
 *
 * <p>Some posts instead cover several areas at once, possibly over two days:
 * {@code "... ժամը 11:00-ից մինչև հոկտեմբերի 22-ը ժամը 11:00-ն կդադարեցվի հետևյալ
 * տարածքների ջրամատակարարումը."} followed by one line per area, each with its own
 * header ({@code "Աջափնյակ վարչական շրջանի՝ X, Y,"}, {@code "Կոտայքի մարզի՝ Z,"}). Those
 * lines become separate {@link OutageAnnouncement.Area}s so a street is only matched
 * within the area it's listed under — see {@link #parseAreaList}.
 */
@Component
public class OutageAnnouncementParser {

    private static final Logger log = LoggerFactory.getLogger(OutageAnnouncementParser.class);

    /** Armenian month names in the genitive form Veolia Jur posts use ("<month>ի <day>-ին"). */
    private static final String MONTHS =
            "հունվարի|փետրվարի|մարտի|ապրիլի|մայիսի|հունիսի|հուլիսի|"
                    + "օգոստոսի|սեպտեմբերի|հոկտեմբերի|նոյեմբերի|դեկտեմբերի";

    // Title line: "Վթարային ջրանջատում <district ...> <month> <day>[-ին]" — the "-ին" case
    // suffix is a grammatical marker some posts omit, so it's optional here.
    // A two-day post's title gives both days ("հոկտեմբերի 21/22-ին"), hence the optional "/22".
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "Վթարային\\s+ջրանջատում\\s+(.+?)\\s+(" + MONTHS + ")\\s+(\\d{1,2}(?:/\\d{1,2})?)(?:-\\S+)?",
            Pattern.DOTALL);

    // The time clause, up to and including "կդադարեցվի", in one of three shapes:
    //   "ժամը 14:00-16:00[-ն] կդադարեցվի"                                   (group 1, 2)
    //   "ժամը 14:00-ից կդադարեցվի"                        — no end time     (group 1)
    //   "ժամը 11:00-ից մինչև [հոկտեմբերի 22-ը] [ժամը] 11:00-ն կդադարեցվի"  (group 1, 3)
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "ժամը\\s+(\\d{1,2}:\\d{2})"
                    + "(?:\\s*-\\s*(\\d{1,2}:\\d{2})(?:-\\S+)?"
                    + "|-ից(?:\\s+մինչև\\s+(?:(?:" + MONTHS + ")\\s+\\d{1,2}(?:-\\S+)?\\s+)?(?:ժամը\\s+)?"
                    + "(\\d{1,2}:\\d{2})(?:-\\S+)?)?)"
                    + "\\s+կդադարեցվի\\s+");

    // What follows "կդադարեցվի" in an ordinary post: "<streets> ջրամատակարարումը".
    private static final Pattern INLINE_STREETS_PATTERN = Pattern.compile("(.+?)\\s+ջրամատակարարումը");

    // ...or, in a multi-area post, "հետևյալ տարածքների ջրամատակարարումը." with the list on the
    // following lines. Matched against the raw text, since the list's line breaks matter.
    private static final Pattern LIST_INTRO_PATTERN = Pattern.compile(
            "կդադարեցվի\\s+հետևյալ\\s+(?:\\S+\\s+){0,4}?ջրամատակարարումը[.:։]?");

    // The closing boilerplate that ends the list.
    private static final Pattern LIST_END_PATTERN = Pattern.compile("Ընկերությունը\\s+հայցում");

    // An area line's header ends with the Armenian "՝" (U+055D): "Կոտայքի մարզի՝ ...".
    private static final char AREA_HEADER_SEPARATOR = '՝';

    private static final Pattern TRAILING_PUNCTUATION_PATTERN = Pattern.compile("[\\s,.:։;]+$");

    // "ամբողջությամբ" ("entirely") in place of a street list: the whole area is affected.
    private static final String WHOLE_AREA_WORD = "ամբողջությամբ";

    // Splits a street/address list on commas and the Armenian conjunction "և"/"եւ" ("and").
    private static final Pattern STREET_SEPARATOR_PATTERN = Pattern.compile("\\s*,\\s*|\\s+և\\s+|\\s+եւ\\s+");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Parses a raw channel post. Returns {@link Optional#empty()} (after
     * logging a warning) if the post doesn't match the expected outage
     * announcement structure — e.g. it's an unrelated announcement from the
     * same channel.
     */
    public Optional<OutageAnnouncement> parse(ChannelPost post) {
        String normalized = normalizeWhitespace(post.text());

        Matcher titleMatcher = TITLE_PATTERN.matcher(normalized);
        if (!titleMatcher.find()) {
            log.warn("Skipping post {}: title doesn't match expected 'Վթարային ջրանջատում ...' pattern", post.postId());
            return Optional.empty();
        }
        String district = titleMatcher.group(1).trim();
        String month = titleMatcher.group(2);
        String day = titleMatcher.group(3);
        String date = month + " " + day;

        Matcher timeMatcher = TIME_PATTERN.matcher(normalized);
        if (!timeMatcher.find()) {
            log.warn("Skipping post {}: could not find a 'ժամը ... կդադարեցվի ... ջրամատակարարումը' clause",
                    post.postId());
            return Optional.empty();
        }
        String startTime = timeMatcher.group(1);
        String endTime = timeMatcher.group(2) != null ? timeMatcher.group(2) : timeMatcher.group(3);

        List<OutageAnnouncement.Area> areas;
        Matcher listIntroMatcher = LIST_INTRO_PATTERN.matcher(post.text());
        if (listIntroMatcher.find()) {
            areas = parseAreaList(post.text().substring(listIntroMatcher.end()), district);
        } else {
            Matcher inlineMatcher = INLINE_STREETS_PATTERN.matcher(normalized);
            if (!inlineMatcher.find(timeMatcher.end())) {
                log.warn("Skipping post {}: could not find a 'ժամը ... կդադարեցվի ... ջրամատակարարումը' clause",
                        post.postId());
                return Optional.empty();
            }
            List<String> streets = splitStreets(inlineMatcher.group(1));
            areas = streets.isEmpty() ? List.of() : List.of(new OutageAnnouncement.Area(district, streets, false));
        }

        List<String> streets = new ArrayList<>();
        areas.forEach(area -> streets.addAll(area.streets()));
        if (streets.isEmpty()) {
            log.warn("Skipping post {}: no street/address fragments extracted", post.postId());
            return Optional.empty();
        }

        return Optional.of(new OutageAnnouncement(post.postId(), district, date, startTime, endTime,
                List.copyOf(streets), List.copyOf(areas), post.text()));
    }

    /**
     * The one-area-per-line list of a multi-area post. A line with a header
     * ({@code "Կոտայքի մարզի՝ ..."}) starts a new area; a line without one continues the
     * previous area (a long list wrapped onto two lines), or, before any header, belongs to
     * the title's area.
     */
    private List<OutageAnnouncement.Area> parseAreaList(String list, String titleScope) {
        Matcher endMatcher = LIST_END_PATTERN.matcher(list);
        if (endMatcher.find()) {
            list = list.substring(0, endMatcher.start());
        }

        List<OutageAnnouncement.Area> areas = new ArrayList<>();
        String scope = titleScope;
        List<String> streets = new ArrayList<>();
        boolean whole = false;
        for (String rawLine : list.split("\\R")) {
            String line = TRAILING_PUNCTUATION_PATTERN.matcher(normalizeWhitespace(rawLine)).replaceAll("");
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf(AREA_HEADER_SEPARATOR);
            if (separator >= 0) {
                if (!streets.isEmpty()) {
                    areas.add(new OutageAnnouncement.Area(scope, List.copyOf(streets), whole));
                }
                String header = line.substring(0, separator).trim();
                scope = namesKnownArea(header) ? header : header + " " + titleScope;
                streets = new ArrayList<>();
                whole = false;
                line = line.substring(separator + 1).trim();
            }
            whole |= line.contains(WHOLE_AREA_WORD);
            streets.addAll(splitStreets(line));
        }
        if (!streets.isEmpty()) {
            areas.add(new OutageAnnouncement.Area(scope, List.copyOf(streets), whole));
        }
        return areas;
    }

    /**
     * Whether an area header names a region or Yerevan district itself ("Կոտայքի մարզի",
     * "Աջափնյակ վարչական շրջանի"). A header that doesn't ("Զովունի գյուղի") is a place within
     * the title's area, so it keeps the title as part of its scope — otherwise a subscription
     * scoped to that region could never match it.
     */
    private static boolean namesKnownArea(String header) {
        String spaced = header.replace('-', ' ');
        for (Region region : Region.values()) {
            if (spaced.contains(region.armenian())) {
                return true;
            }
        }
        for (District district : District.values()) {
            if (spaced.contains(district.armenian().replace('-', ' '))) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitStreets(String streetsRaw) {
        List<String> streets = new ArrayList<>();
        for (String fragment : STREET_SEPARATOR_PATTERN.split(streetsRaw)) {
            String trimmed = fragment.trim();
            if (!trimmed.isEmpty()) {
                streets.add(trimmed);
            }
        }
        return streets;
    }

    private String normalizeWhitespace(String text) {
        return WHITESPACE_PATTERN.matcher(text.trim()).replaceAll(" ");
    }
}
