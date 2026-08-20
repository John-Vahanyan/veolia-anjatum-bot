package am.veolia.bot.parser;

import am.veolia.bot.model.OutageAnnouncement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMatcherTest {

    private final KeywordMatcher matcher = new KeywordMatcher();

    private static OutageAnnouncement announcement(String district, List<String> streets) {
        return new OutageAnnouncement("VeoliaJur/1", district, "օգոստոսի 16", "14:00", "16:00", streets, "raw");
    }

    @Test
    void matchesAStreetSubstringCaseInsensitively() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matches("Մանթաշյան", a)).isTrue();
        assertThat(matcher.matches("մանթաշյան", a)).isTrue();
    }

    @Test
    void matchesAgainstTheDistrictNameToo() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում", List.of("Մանթաշյան 6-12"));

        assertThat(matcher.matches("Շենգավիթ", a)).isTrue();
    }

    @Test
    void doesNotMatchAnUnrelatedKeyword() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում", List.of("Մանթաշյան 6-12"));

        assertThat(matcher.matches("Կոմիտաս", a)).isFalse();
    }

    @Test
    void ignoresPunctuationAndExtraWhitespaceDifferences() {
        OutageAnnouncement a = announcement("Երևանի Կենտրոն", List.of("Թումանյան 10,"));

        assertThat(matcher.matches("թումանյան   10", a)).isTrue();
    }

    @Test
    void blankKeywordNeverMatches() {
        OutageAnnouncement a = announcement("Երևանի Կենտրոն", List.of("Թումանյան 10"));

        assertThat(matcher.matches("   ", a)).isFalse();
    }

    @Test
    void toleratesUpToTwoLetterDifferences_forKeywordsLongEnough() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        // "Մանթաշյան" with its 1st and last letter swapped -- exactly 2 substitutions away,
        // simulating imperfect Latin/Russian-to-Armenian transliteration.
        assertThat(matcher.matches("Լանթաշյալ", a)).isTrue();
    }

    @Test
    void rejectsThreeOrMoreLetterDifferences() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        // 3 substitutions away from "Մանթաշյան" -- beyond the 2-edit tolerance.
        assertThat(matcher.matches("Լանդաշյալ", a)).isFalse();
    }

    @Test
    void doesNotApplyFuzzyToleranceToVeryShortKeywords() {
        OutageAnnouncement a = announcement("Երևանի Կենտրոն", List.of("Աբովյան 22"));

        // "բոգ" is a single substitution away from "բով" (chars 2-4 of "Աբովյան") -- close
        // enough that fuzzy tolerance would match it, but it's only 3 characters, below the
        // fuzzy-matching floor, so it must be rejected instead of matching almost anything short.
        assertThat(matcher.matches("բոգ", a)).isFalse();
    }

    @Test
    void scopedMatchRequiresBothTheScopeAndTheStreetToMatch() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matchesScoped("Շենգավիթ", "Մանթաշյան", a)).isTrue();
    }

    @Test
    void scopedMatchRejectsAMatchingStreetInTheWrongDistrict() {
        // Same street name, but the announcement is for a different district than the one
        // the user scoped their subscription to -- this is exactly the false-positive the
        // guided region/district flow exists to prevent.
        OutageAnnouncement a = announcement("Երևանի Կենտրոն վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matchesScoped("Շենգավիթ", "Մանթաշյան", a)).isFalse();
    }

    @Test
    void scopedMatchRejectsTheRightDistrictWithAnUnrelatedStreet() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matchesScoped("Շենգավիթ", "Կոմիտաս", a)).isFalse();
    }

    @Test
    void matchesScopedAcceptsARegionScopeAgainstTheDistrictField() {
        OutageAnnouncement a = announcement("Կոտայքի մարզի Բլահովիտ գյուղում",
                List.of("1", "8", "9 փողոցների"));

        assertThat(matcher.matchesScoped("Կոտայք", "8", a)).isTrue();
    }
}
