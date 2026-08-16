package am.veolia.bot.parser;

import am.veolia.bot.model.OutageAnnouncement;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Decides whether a user's subscribed keyword (a street or district name)
 * matches a parsed outage announcement.
 *
 * <p>Armenian script has no upper/lower case distinction the way Latin
 * script does, so there's no case-folding step needed for correctness —
 * but we still lower-case defensively in case a keyword or post mixes in
 * Latin characters (e.g. a building number suffix), and we collapse
 * whitespace/punctuation so trivial formatting differences don't block a match.
 */
@Component
public class KeywordMatcher {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    // Collapses punctuation that commonly varies between how a street is written
    // in a post vs. how a user might type it (commas, dashes, colons, quotes).
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[,.:;«»\"'()]");

    /** True if {@code keyword} appears as a substring of the district or any street fragment. */
    public boolean matches(String keyword, OutageAnnouncement announcement) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return false;
        }
        return announcement.matchableFragments().stream()
                .map(this::normalize)
                .anyMatch(fragment -> fragment.contains(normalizedKeyword));
    }

    /** Trims, collapses whitespace, strips common punctuation, and lower-cases for comparison. */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).trim();
        normalized = PUNCTUATION_PATTERN.matcher(normalized).replaceAll("");
        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");
        return normalized.toLowerCase();
    }
}
