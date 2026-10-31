package com.blusimian.blusimian.platform.tenancy;

import com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.Policy;
import com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.Privilege;
import com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.Table;
import com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.View;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.TENANTS_POLICY;
import static com.blusimian.blusimian.platform.tenancy.RowLevelSecurityVerifier.TENANT_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

/** The startup checks' rules, one passing and one failing input each. */
class RowLevelSecurityVerifierTest {

    private static final String ROLE = "blusimian_app";
    private static final Table COMPANIES = new Table("companies", true, true);
    private static final Table TENANTS = new Table("tenants", true, false);

    private static Policy isolation(String table, String expression) {
        return new Policy(table, "tenant_isolation", true, ROLE, "ALL", expression, expression);
    }

    @Test
    void c1_noRoleMayBypassRls() {
        assertThat(RowLevelSecurityVerifier.checkBypassRoles(List.of())).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkBypassRoles(List.of("postgres"))).singleElement()
                .asString().startsWith("C1").contains("postgres");
    }

    @Test
    void c2_theAppRoleOwnsNothing() {
        assertThat(RowLevelSecurityVerifier.checkOwnedRelations(List.of())).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkOwnedRelations(List.of("companies"))).singleElement()
                .asString().startsWith("C2").contains("app.companies");
    }

    @Test
    void c3_everyTableHasRlsAndATenantIdExceptTenantsItself() {
        assertThat(RowLevelSecurityVerifier.checkTables(List.of(COMPANIES, TENANTS))).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkTables(List.of(
                new Table("deals", false, true),
                new Table("notes", true, false))))
                .containsExactly(
                        "C3: app.deals has row-level security disabled",
                        "C3: app.notes has no tenant_id column");
    }

    @Test
    void c4_eachTableHasExactlyTheIsolationPolicyAndOnlyRestrictiveOthers() {
        Policy restrictive = new Policy("companies", "owner_only", false, ROLE, "SELECT", "(true)", null);
        assertThat(RowLevelSecurityVerifier.checkPolicies(List.of(COMPANIES, TENANTS),
                List.of(isolation("companies", TENANT_POLICY), restrictive, isolation("tenants", TENANTS_POLICY)),
                ROLE)).isEmpty();

        Policy tooWide = new Policy("companies", "everything", true, ROLE, "ALL", "true", "true");
        Policy missing = new Policy("tenants", "tenant_isolation", true, "public", "ALL", TENANTS_POLICY, TENANTS_POLICY);
        assertThat(RowLevelSecurityVerifier.checkPolicies(List.of(COMPANIES, TENANTS, new Table("deals", true, true)),
                List.of(isolation("companies", TENANT_POLICY), tooWide, missing), ROLE))
                .satisfiesExactly(
                        companies -> assertThat(companies).startsWith("C4: app.companies has 2 permissive"),
                        tenants -> assertThat(tenants).startsWith("C4: app.tenants policy tenant_isolation is not exactly"),
                        deals -> assertThat(deals).startsWith("C4: app.deals has 0 permissive"));
    }

    @Test
    void c5_onlyDmlIsGranted() {
        assertThat(RowLevelSecurityVerifier.checkPrivileges(List.of())).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkPrivileges(List.of(new Privilege("companies", "TRUNCATE"))))
                .singleElement().asString().startsWith("C5").contains("TRUNCATE");
    }

    @Test
    void c6_noDefinerRightsOrMaterializedViews() {
        assertThat(RowLevelSecurityVerifier.checkViews(List.of())).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkViews(List.of(new View("report", "v"), new View("totals", "m"))))
                .containsExactly(
                        "C6: app.report is a view without security_invoker",
                        "C6: app.totals is a materialized view, which can't have RLS");
    }

    @Test
    void c7_onlyAllowlistedDefinerFunctions() {
        assertThat(RowLevelSecurityVerifier.checkDefinerFunctions(List.of("find_login_user"))).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkDefinerFunctions(List.of("find_login_user", "all_companies")))
                .singleElement().asString().startsWith("C7").contains("all_companies");
    }

    @Test
    void c8_aConnectionCarriesNoTenantOfItsOwn() {
        assertThat(RowLevelSecurityVerifier.checkSessionTenant("")).isEmpty();
        assertThat(RowLevelSecurityVerifier.checkSessionTenant("10000000-0000-0000-0000-000000000001"))
                .singleElement().asString().startsWith("C8");
    }
}
