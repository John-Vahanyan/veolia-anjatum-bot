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
    void treatsAHyphenatedDistrictNameAsEquivalentToTheSpacedForm() {
        // Real posts inconsistently render this district as "Մալաթիա-Սեբաստիա" or
        // "Մալաթիա Սեբաստիա" -- the scope check (never fuzzy) must still match either way.
        OutageAnnouncement hyphenated = announcement("Երևանի Մալաթիա-Սեբաստիա վարչական շրջանում",
                List.of("Իսրայել Օրի"));
        OutageAnnouncement spaced = announcement("Երևանի Մալաթիա Սեբաստիա վարչական շրջանում",
                List.of("Իսրայել Օրի"));

        assertThat(matcher.matchesAny("Մալաթիա-Սեբաստիա", List.of(spaced.district()), false)).isTrue();
        assertThat(matcher.matchesAny("Մալաթիա Սեբաստիա", List.of(hyphenated.district()), false)).isTrue();
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
    void treatsTheAbbreviatedStreetWordAsEquivalentToTheFullWord() {
        // Posts sometimes abbreviate "փողոց" ("street") down to just "փ" -- a keyword typed
        // with the full word must still match the abbreviated form, and vice versa.
        OutageAnnouncement abbreviated = announcement("Երևանի Կենտրոն", List.of("Կոմիտաս փ."));

        assertThat(matcher.matches("Կոմիտաս փողոց", abbreviated)).isTrue();
        assertThat(matcher.matches("Կոմիտաս փողոցի", abbreviated)).isTrue();
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

    @Test
    void allowFuzzyFalseRejectsAnAlmostMatchingKeyword() {
        // Same 1st/last-letter-swap as toleratesUpToTwoLetterDifferences_forKeywordsLongEnough,
        // but with fuzzy tolerance turned off -- meant for a keyword the user typed directly in
        // Armenian, where there's no transliteration slack to excuse a near-miss.
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matches("Լանթաշյալ", a, false)).isFalse();
    }

    @Test
    void allowFuzzyFalseStillAcceptsAnExactMatch() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matches("Մանթաշյան", a, false)).isTrue();
    }

    @Test
    void matchesScopedWithFuzzyDisabledForTheStreetRejectsAnAlmostMatchingStreet() {
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matchesScoped("Շենգավիթ", "Լանթաշյալ", a, false)).isFalse();
    }

    @Test
    void matchesScopedNeverAppliesFuzzyToleranceToTheScopeItselfEvenWhenAllowed() {
        // The district field says "Շենգավիթ", not the near-miss "Շենգավիլ" -- the scope check
        // is always exact-contains, regardless of allowFuzzyForStreet, since it's always a
        // canonical name picked via button, never typed.
        OutageAnnouncement a = announcement("Երևանի Շենգավիթ վարչական շրջանում",
                List.of("Մանթաշյան 6-12 զույգ համարի շենքերի"));

        assertThat(matcher.matchesScoped("Շենգավիլ", "Մանթաշյան", a, true)).isFalse();
    }

    // ---- Multi-area posts ----

    private OutageAnnouncement multiAreaPost() {
        return new OutageAnnouncementParser()
                .parse(new am.veolia.bot.model.ChannelPost("VeoliaJur/2000", OutageAnnouncementParserTest.MULTI_AREA_POST))
                .orElseThrow();
    }

    @Test
    void multiAreaPostMatchesAStreetUnderItsOwnArea() {
        OutageAnnouncement a = multiAreaPost();
        assertThat(matcher.matchesScoped("Աջափնյակ", "Մարգարյան", a, false)).isTrue();
        assertThat(matcher.matchesScoped("Կոտայք", "Եղվարդ", a, false)).isTrue();
        assertThat(matcher.matchesScoped("Արագածոտն", "Սաղմոսավան", a, false)).isTrue();
    }

    @Test
    void multiAreaPostDoesNotMatchAStreetListedUnderAnotherArea() {
        OutageAnnouncement a = multiAreaPost();
        // Եղվարդ is listed under Kotayk, not Aragatsotn; Մարգարյան under Ajapnyak, not Kentron.
        assertThat(matcher.matchesScoped("Արագածոտն", "Եղվարդ", a, false)).isFalse();
        assertThat(matcher.matchesScoped("Կենտրոն", "Մարգարյան", a, false)).isFalse();
    }

    @Test
    void multiAreaPostMatchesAnyStreetInAnAreaAffectedAsAWhole() {
        // "Դավթաշեն վարչական շրջանը՝ ամբողջությամբ" — every Davtashen street is out.
        assertThat(matcher.matchesScoped("Դավթաշեն", "Սասունցի Դավթի", multiAreaPost(), false)).isTrue();
    }

    @Test
    void multiAreaPostMatchesWholeRegionAndDistrictSubscriptions() {
        OutageAnnouncement a = multiAreaPost();
        assertThat(matcher.matches("Դավթաշեն", a, false)).isTrue();
        assertThat(matcher.matches("Աջափնյակ", a, false)).isTrue();
        assertThat(matcher.matches("Կոտայք", a, false)).isTrue();
        assertThat(matcher.matches("Արագածոտն", a, false)).isTrue();
        assertThat(matcher.matches("Շենգավիթ", a, false)).isFalse();
    }
}
