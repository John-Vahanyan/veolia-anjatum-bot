package am.veolia.bot.parser;

import am.veolia.bot.model.OutageAnnouncement;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
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
 *
 * <p>Matching can optionally be fuzzy (edit-distance-tolerant) rather than a
 * plain substring check: a keyword that started life as a
 * {@link Transliterator}-converted Latin/Russian street name is, at best, an
 * approximation of the real Armenian spelling, so a small amount of drift is
 * expected there, not exceptional — but a keyword the user typed directly in
 * Armenian has no such excuse, and tolerating "close enough" for it only
 * risks matching an unrelated, similarly-spelled street. So fuzzy tolerance
 * is an explicit, per-call opt-in ({@code allowFuzzy}); callers pass the
 * subscription's own record of whether it was transliterated (see
 * {@code Subscription#fuzzyMatch}). An exact substring match is always tried
 * first regardless, as a fast path; the fuzzy fallback only kicks in for
 * keywords long enough that a couple of stray edits can't turn them into
 * nonsense matches (see {@link #MIN_LENGTH_FOR_FUZZY}).
 */
@Component
public class KeywordMatcher {

    /** Allowed edit distance (insert/delete/substitute) for the fuzzy fallback. */
    private static final int MAX_EDITS = 2;

    /**
     * Fuzzy matching only applies to keywords at least this long. Without this
     * floor, a 2-character keyword with 2 allowed edits could match almost
     * anything of similar length — the tolerance has to scale with how much
     * signal is actually in the keyword.
     */
    private static final int MIN_LENGTH_FOR_FUZZY = 4;

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    // Collapses punctuation that commonly varies between how a street is written
    // in a post vs. how a user might type it (commas, dashes, colons, quotes).
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[,.:;«»\"'()]");

    /** {@link #matches(String, OutageAnnouncement, boolean)} with fuzzy tolerance allowed. */
    public boolean matches(String keyword, OutageAnnouncement announcement) {
        return matches(keyword, announcement, true);
    }

    /** True if {@code keyword} matches the district or any street fragment, fuzzily iff {@code allowFuzzy}. */
    public boolean matches(String keyword, OutageAnnouncement announcement, boolean allowFuzzy) {
        return matchesAny(keyword, announcement.matchableFragments(), allowFuzzy);
    }

    /** {@link #matchesAny(String, List, boolean)} with fuzzy tolerance allowed. */
    public boolean matchesAny(String keyword, List<String> fragments) {
        return matchesAny(keyword, fragments, true);
    }

    /**
     * True if {@code keyword} matches at least one of {@code fragments}, fuzzily iff
     * {@code allowFuzzy}. This is the building block both {@link #matches} (checks every
     * fragment of an announcement) and scoped matching (checks the district fragment and the
     * street fragments separately) are built from.
     */
    public boolean matchesAny(String keyword, List<String> fragments, boolean allowFuzzy) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return false;
        }
        return fragments.stream()
                .map(this::normalize)
                .anyMatch(fragment -> contains(fragment, normalizedKeyword, allowFuzzy));
    }

    /** {@link #matchesScoped(String, String, OutageAnnouncement, boolean)} with fuzzy tolerance allowed for the street. */
    public boolean matchesScoped(String scopeArmenianName, String streetKeyword, OutageAnnouncement announcement) {
        return matchesScoped(scopeArmenianName, streetKeyword, announcement, true);
    }

    /**
     * True if the announcement is both in {@code scopeArmenianName}'s region/district (its
     * district fragment matches) <em>and</em> some street fragment matches {@code streetKeyword}
     * (fuzzily iff {@code allowFuzzyForStreet}). Requiring both stops a street subscription
     * from firing on a similarly-spelled street in a completely different part of the country —
     * the failure mode that plain, unscoped fuzzy matching had.
     *
     * <p>The scope check itself is never fuzzy: {@code scopeArmenianName} always comes from the
     * fixed {@link am.veolia.bot.model.Region}/{@link am.veolia.bot.model.District} list, picked
     * via button, never typed — there's no transliteration slack to absorb there.
     */
    public boolean matchesScoped(String scopeArmenianName, String streetKeyword, OutageAnnouncement announcement,
                                  boolean allowFuzzyForStreet) {
        return matchesAny(scopeArmenianName, List.of(announcement.district()), false)
                && matchesAny(streetKeyword, announcement.streets(), allowFuzzyForStreet);
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

    private boolean contains(String text, String pattern, boolean allowFuzzy) {
        if (pattern.isEmpty()) {
            return false;
        }
        if (text.contains(pattern)) {
            return true;
        }
        if (!allowFuzzy || pattern.length() < MIN_LENGTH_FOR_FUZZY) {
            return false;
        }
        return approximateSubstringEditDistance(text, pattern) <= MAX_EDITS;
    }

    /**
     * Minimum edit distance between {@code pattern} and its best-matching
     * substring anywhere within {@code text} (classic approximate/fuzzy
     * substring search — not the same as a whole-string edit distance).
     *
     * <p>Standard Levenshtein DP with one change: the first row is seeded
     * with all zeros instead of 0..n, meaning a match is allowed to "start"
     * at any position in {@code text} for free, since we only care whether
     * some substring of {@code text} is close to {@code pattern}, not
     * whether the whole of {@code text} is.
     */
    private int approximateSubstringEditDistance(String text, String pattern) {
        int n = text.length();
        int m = pattern.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int j = 0; j <= n; j++) {
            dp[0][j] = 0;
        }
        for (int i = 1; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (pattern.charAt(i - 1) == text.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        int min = Integer.MAX_VALUE;
        for (int j = 0; j <= n; j++) {
            min = Math.min(min, dp[m][j]);
        }
        return min;
    }
}
