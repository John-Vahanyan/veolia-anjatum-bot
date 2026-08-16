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
}
