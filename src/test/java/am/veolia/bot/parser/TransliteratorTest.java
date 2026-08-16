package am.veolia.bot.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransliteratorTest {

    @Test
    void leavesAlreadyArmenianTextUnchanged() {
        assertThat(Transliterator.toArmenianBestEffort("Մանթաշյան")).isEqualTo("Մանթաշյան");
    }

    @Test
    void leavesBareNumbersAndPunctuationUnchanged() {
        assertThat(Transliterator.toArmenianBestEffort("6-12")).isEqualTo("6-12");
    }

    @Test
    void transliteratesLatinDigraphsBeforeSingleLetters() {
        // "sh" must become շ, not ս+հ.
        assertThat(Transliterator.toArmenianBestEffort("sh")).isEqualTo("շ");
        assertThat(Transliterator.toArmenianBestEffort("ch")).isEqualTo("չ");
    }

    @Test
    void transliteratesALatinStreetNameToAPlausibleArmenianApproximation() {
        String result = Transliterator.toArmenianBestEffort("Komitas");
        // Not asserting an exact "correct" spelling (there isn't a single correct one from
        // Latin alone) -- just that it produced Armenian script and preserved the digit-free
        // structure, which is what downstream fuzzy matching relies on.
        assertThat(result).isNotEqualTo("Komitas");
        assertThat(result).matches("[\\p{IsArmenian}]+");
    }

    @Test
    void transliteratesCyrillicToArmenian() {
        String result = Transliterator.toArmenianBestEffort("Комитас");
        assertThat(result).isNotEqualTo("Комитас");
        assertThat(result).matches("[\\p{IsArmenian}]+");
    }

    @Test
    void preservesNonLetterCharactersWhileTransliterating() {
        String result = Transliterator.toArmenianBestEffort("Mantashyan 6-12");
        assertThat(result).contains("6-12");
        assertThat(result).doesNotContain("Mantashyan");
    }

    @Test
    void mixedArmenianAndLatinIsLeftUntouched() {
        // Presence of any Armenian character is treated as "already Armenian, don't touch it".
        String mixed = "Մանթաշյան St";
        assertThat(Transliterator.toArmenianBestEffort(mixed)).isEqualTo(mixed);
    }
}
