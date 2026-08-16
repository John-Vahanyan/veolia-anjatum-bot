package am.veolia.bot.parser;

import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.OutageAnnouncement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "Վթարային\\s+ջրանջատում\\s+(.+?)\\s+(" + MONTHS + ")\\s+(\\d{1,2})(?:-\\S+)?",
            Pattern.DOTALL);

    // Time range + streets: "ժամը 14:00-16:00[-ն] կդադարեցվի <streets> ջրամատակարարումը"
    private static final Pattern TIME_RANGE_AND_STREETS_PATTERN = Pattern.compile(
            "ժամը\\s+(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})(?:-\\S+)?\\s+կդադարեցվի\\s+(.+?)\\s+ջրամատակարարումը",
            Pattern.DOTALL);

    // Open-ended time: "ժամը 14:00-ից կդադարեցվի <streets> ջրամատակարարումը" (no end time given)
    private static final Pattern TIME_OPEN_AND_STREETS_PATTERN = Pattern.compile(
            "ժամը\\s+(\\d{1,2}:\\d{2})-ից\\s+կդադարեցվի\\s+(.+?)\\s+ջրամատակարարումը",
            Pattern.DOTALL);

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

        Matcher rangeMatcher = TIME_RANGE_AND_STREETS_PATTERN.matcher(normalized);
        String startTime;
        String endTime;
        String streetsRaw;
        if (rangeMatcher.find()) {
            startTime = rangeMatcher.group(1);
            endTime = rangeMatcher.group(2);
            streetsRaw = rangeMatcher.group(3);
        } else {
            Matcher openMatcher = TIME_OPEN_AND_STREETS_PATTERN.matcher(normalized);
            if (openMatcher.find()) {
                startTime = openMatcher.group(1);
                endTime = null;
                streetsRaw = openMatcher.group(2);
            } else {
                log.warn("Skipping post {}: could not find a 'ժամը ... կդադարեցվի ... ջրամատակարարումը' clause",
                        post.postId());
                return Optional.empty();
            }
        }

        List<String> streets = splitStreets(streetsRaw);
        if (streets.isEmpty()) {
            log.warn("Skipping post {}: no street/address fragments extracted", post.postId());
            return Optional.empty();
        }

        return Optional.of(new OutageAnnouncement(post.postId(), district, date, startTime, endTime, streets, post.text()));
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
