package com.blusimian.blusimian.picklists;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Deriving a code from a label (spec §10.3). */
class PicklistCodesTest {

    private static String derive(String label, String... taken) {
        return PicklistCodes.derive(label, Set.of(taken));
    }

    @Test
    void aLabelBecomesSnakeCase() {
        assertThat(derive("Food & Beverage")).isEqualTo("food_beverage");
        assertThat(derive("Not for Profit")).isEqualTo("not_for_profit");
        assertThat(derive("  --Cold outreach!  ")).isEqualTo("cold_outreach");
        assertThat(derive("10,001+")).isEqualTo("10_001");
    }

    @Test
    void accentsAreStripped() {
        assertThat(derive("Café")).isEqualTo("cafe");
        assertThat(derive("Ångström Øre")).isEqualTo("angstrom_re");
    }

    @Test
    void aLabelWithNoLatinLettersOrDigitsBecomesValue() {
        assertThat(derive("日本")).isEqualTo("value");
        assertThat(derive("&&&")).isEqualTo("value");
    }

    @Test
    void aTakenCodeGetsTheNextFreeSuffix() {
        assertThat(derive("Retail", "retail")).isEqualTo("retail_2");
        assertThat(derive("Retail", "retail", "retail_2")).isEqualTo("retail_3");
        assertThat(derive("日本", "value")).isEqualTo("value_2");
    }

    @Test
    void theCodeIsCutToFiftyCharactersWithoutATrailingUnderscore() {
        String label = "a".repeat(49) + " b";   // the cut lands on the separator
        assertThat(derive(label)).isEqualTo("a".repeat(49));
        assertThat(derive("x".repeat(80))).hasSize(50);
    }

    @Test
    void theSuffixStaysWithinFiftyCharacters() {
        String code = derive("x".repeat(80), "x".repeat(50));
        assertThat(code).hasSize(50).endsWith("_2");
        assertThat(PicklistCodes.matchesPattern(code)).isTrue();
    }

    @Test
    void everyDerivedCodeMatchesThePattern() {
        for (String label : new String[]{"Food & Beverage", "Café", "日本", "_a_", "1–10", "Mx."}) {
            assertThat(PicklistCodes.matchesPattern(derive(label))).as(label).isTrue();
        }
    }
}
