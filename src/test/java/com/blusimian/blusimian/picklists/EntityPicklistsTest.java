package com.blusimian.blusimian.picklists;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The three rules a picklist write must meet (spec §6), without a database. */
class EntityPicklistsTest {

    private static final List<PicklistOption> INDUSTRY = List.of(
            new PicklistOption("technology", "Technology", true),
            new PicklistOption("shipping", "Shipping", false),
            new PicklistOption("retail", "Retail", true));

    private final EntityPicklists picklists = new EntityPicklists(Map.of("industry", INDUSTRY));

    @Test
    void nullIsAlwaysAllowed() {
        assertThat(picklists.allows("industry", null, "technology")).isTrue();
        assertThat(picklists.allows("no_such_field", null, null)).isTrue();
    }

    @Test
    void anActiveCodeIsAllowed() {
        assertThat(picklists.allows("industry", "technology", null)).isTrue();
        assertThat(picklists.allows("industry", "retail", "technology")).isTrue();
    }

    @Test
    void anInactiveCodeIsAllowedOnlyWhenTheRecordAlreadyHoldsIt() {
        assertThat(picklists.allows("industry", "shipping", null)).isFalse();
        assertThat(picklists.allows("industry", "shipping", "technology")).isFalse();
        assertThat(picklists.allows("industry", "shipping", "shipping")).isTrue();
    }

    @Test
    void aCodeNotInTheListIsAllowedOnlyWhenUnchanged() {
        assertThat(picklists.allows("industry", "software", null)).isFalse();
        assertThat(picklists.allows("industry", "legacy_value", "legacy_value")).isTrue();
    }

    @Test
    void codesAreCaseSensitiveAndTheEmptyStringIsNotACode() {
        assertThat(picklists.allows("industry", "Technology", null)).isFalse();
        assertThat(picklists.allows("industry", "", null)).isFalse();
    }

    @Test
    void fieldKeysMatchCaseInsensitively() {
        assertThat(picklists.optionsFor("INDUSTRY")).isEqualTo(INDUSTRY);
        assertThat(picklists.allows("Industry", "technology", null)).isTrue();
    }

    @Test
    void optionsKeepTheirOrderAndIncludeInactiveOnes() {
        assertThat(picklists.optionsFor("industry")).extracting(PicklistOption::code)
                .containsExactly("technology", "shipping", "retail");
    }

    @Test
    void anUnknownFieldHasNoOptionsAndAllowsNoValue() {
        assertThat(picklists.optionsFor("lead_source")).isEmpty();
        assertThat(picklists.allows("lead_source", "web", null)).isFalse();
        assertThat(EntityPicklists.EMPTY.optionsFor("industry")).isEmpty();
    }
}
