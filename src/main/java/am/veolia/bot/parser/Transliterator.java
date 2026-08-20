package am.veolia.bot.parser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Best-effort phonetic transliteration of Latin or Cyrillic text into
 * Armenian script, for users who type a street/district name in English or
 * Russian letters (Veolia Jur only ever posts in Armenian, so an
 * un-transliterated Latin/Cyrillic keyword would never match anything).
 *
 * <p><b>This is deliberately a heuristic, not an authoritative transform.</b>
 * Armenian has phonetic distinctions — aspirated vs. plain consonants
 * (t/թ vs. տ), for instance — that Latin or Cyrillic spelling alone cannot
 * unambiguously encode, so a "best guess" mapping is the ceiling of what's
 * achievable here. {@link KeywordMatcher}'s fuzzy (edit-distance-tolerant)
 * matching exists specifically to absorb the resulting slack — this class
 * intentionally doesn't try to be perfect on its own.
 *
 * <p>Digraphs (e.g. "sh" → շ) are matched greedily before falling back to
 * single-character mapping, so "sh" doesn't become ս+հ.
 */
public final class Transliterator {

    private static final Pattern ARMENIAN = Pattern.compile("[\\u0530-\\u058F\\uFB13-\\uFB17]");
    private static final Pattern CYRILLIC = Pattern.compile("[\\u0400-\\u04FF]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    // Ordered longest-pattern-first so e.g. "sh" is tried before "s" alone.
    private static final Map<String, String> LATIN_TO_ARMENIAN = orderedMap(
            "yev", "և",
            "sh", "շ", "ch", "չ", "zh", "ժ", "kh", "խ", "gh", "ղ",
            "dz", "ձ", "ts", "ց", "tz", "ծ", "th", "թ", "ph", "փ",
            "ye", "ե", "vo", "ո", "ou", "ու",
            "a", "ա", "b", "բ", "c", "ց", "d", "դ", "e", "ե", "f", "ֆ",
            "g", "գ", "h", "հ", "i", "ի", "j", "ջ", "k", "կ", "l", "լ",
            "m", "մ", "n", "ն", "o", "ո", "p", "պ", "q", "ք", "r", "ր",
            "s", "ս", "t", "տ", "u", "ու", "v", "վ", "w", "վ", "x", "խ",
            "y", "յ", "z", "զ"
    );

    // Russian already has dedicated single letters for most sh/ch/zh-type
    // sounds, so far fewer digraphs are needed than for Latin.
    private static final Map<String, String> CYRILLIC_TO_ARMENIAN = orderedMap(
            "а", "ա", "б", "բ", "в", "վ", "г", "գ", "д", "դ", "е", "ե",
            "ё", "ո", "ж", "ժ", "з", "զ", "и", "ի", "й", "յ", "к", "կ",
            "л", "լ", "м", "մ", "н", "ն", "о", "ո", "п", "պ", "р", "ր",
            "с", "ս", "т", "տ", "у", "ու", "ф", "ֆ", "х", "խ", "ц", "ց",
            "ч", "չ", "ш", "շ", "щ", "շ", "ъ", "", "ы", "ը", "ь", "",
            "э", "է", "ю", "յու", "я", "յա"
    );

    private Transliterator() {
    }

    /**
     * Converts {@code text} to Armenian if it's Latin- or Cyrillic-lettered;
     * returns it unchanged if it already contains Armenian script (nothing to
     * do) or contains neither letter set (e.g. a bare building number).
     */
    public static String toArmenianBestEffort(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (ARMENIAN.matcher(text).find()) {
            return text;
        }
        if (CYRILLIC.matcher(text).find()) {
            return transliterate(text, CYRILLIC_TO_ARMENIAN);
        }
        if (LATIN.matcher(text).find()) {
            return transliterate(text, LATIN_TO_ARMENIAN);
        }
        return text;
    }

    private static String transliterate(String text, Map<String, String> table) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        int i = 0;
        outer:
        while (i < lower.length()) {
            for (Map.Entry<String, String> entry : table.entrySet()) {
                String pattern = entry.getKey();
                if (lower.regionMatches(i, pattern, 0, pattern.length())) {
                    result.append(entry.getValue());
                    i += pattern.length();
                    continue outer;
                }
            }
            // Unmapped character (digit, space, punctuation, dash, ...): keep as-is.
            result.append(lower.charAt(i));
            i++;
        }
        return result.toString();
    }

    /** Builds a LinkedHashMap (insertion order preserved) from alternating key/value varargs. */
    private static Map<String, String> orderedMap(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}
