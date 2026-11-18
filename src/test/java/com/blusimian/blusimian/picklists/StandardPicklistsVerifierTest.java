package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.picklists.StandardPicklistsVerifier.PicklistField;
import com.blusimian.blusimian.picklists.StandardPicklistsVerifier.SeedRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The startup comparison, without a database. */
class StandardPicklistsVerifierTest {

    private static final List<PicklistField> FIELDS = List.of(
            new PicklistField("COMPANY", "industry", 100),
            new PicklistField("COMPANY", "employeeRange", 10));

    private static final List<SeedRow> SEED = List.of(
            new SeedRow("COMPANY", "industry", "technology"),
            new SeedRow("COMPANY", "employeeRange", "1_10"));

    private static List<SeedRow> seedPlus(SeedRow... rows) {
        List<SeedRow> seed = new java.util.ArrayList<>(SEED);
        seed.addAll(List.of(rows));
        return seed;
    }

    @Test
    void aMatchingSeedPasses() {
        assertThat(StandardPicklistsVerifier.compare(FIELDS, SEED)).isEmpty();
    }

    @Test
    void aPicklistFieldWithNoSeedIsReported() {
        assertThat(StandardPicklistsVerifier.compare(FIELDS, SEED.subList(0, 1)))
                .containsExactly("COMPANY.employeeRange is a PICKLIST field with no rows in public.standard_picklist_values");
    }

    @Test
    void aSeedForAFieldThatIsntAStandardPicklistIsReported() {
        assertThat(StandardPicklistsVerifier.compare(FIELDS, seedPlus(new SeedRow("COMPANY", "website", "x"))))
                .containsExactly("public.standard_picklist_values has rows for COMPANY.website, "
                        + "which is not a standard PICKLIST field");
    }

    @Test
    void theSeedMustSpellTheKeyAsTheProviderDoes() {
        assertThat(StandardPicklistsVerifier.compare(FIELDS, List.of(
                new SeedRow("COMPANY", "industry", "technology"),
                new SeedRow("COMPANY", "employee_range", "1_10"))))
                .containsExactly(
                        "COMPANY.employeeRange is a PICKLIST field with no rows in public.standard_picklist_values",
                        "public.standard_picklist_values has rows for COMPANY.employee_range, "
                                + "which is not a standard PICKLIST field");
    }

    @Test
    void aCodeOverTheFieldsMaxLengthIsReported() {
        assertThat(StandardPicklistsVerifier.compare(FIELDS,
                seedPlus(new SeedRow("COMPANY", "employeeRange", "10001_plus_x"))))
                .containsExactly("code 10001_plus_x of COMPANY.employeeRange is longer than the field's 10 characters");
    }
}
