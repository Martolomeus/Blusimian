package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldDefinition;
import com.blusimian.blusimian.customFields.CustomFieldDefinitionRequest;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.roles.dto.AppPermissions;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import com.blusimian.blusimian.roles.dto.RoleRequest;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import com.blusimian.blusimian.roles.exception.AdministratorRoleLockedException;
import com.blusimian.blusimian.roles.exception.RoleNameTakenException;
import com.blusimian.blusimian.roles.exception.RoleNotFoundException;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The role document's life cycle, as the service sees it. */
@SpringBootTest
class RoleServiceIntegrationTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleService roles;
    @Autowired
    CustomFieldService customFields;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transaction;

    TenantId tenant;

    @BeforeEach
    void provisionTenantForTest() {
        tenant = provisionTenant(provisioning);
    }

    private RoleResponse create(RoleRequest request) {
        return callAs(tenant, () -> roles.create(request));
    }

    private RoleResponse replace(UUID id, RoleRequest request) {
        return callAs(tenant, () -> roles.replace(id, request));
    }

    private RoleResponse get(UUID id) {
        return callAs(tenant, () -> roles.get(id));
    }

    private UUID administratorId() {
        return callAs(tenant, () -> roles.list()).stream()
                .filter(RoleSummaryResponse::isAdmin).findFirst().orElseThrow().id();
    }

    private CustomFieldDefinition customField(String key) {
        return callAs(tenant, () -> customFields.create(EntityType.CONTACT,
                new CustomFieldDefinitionRequest(key, key, FieldDataType.TEXT, false, 0)));
    }

    private long rowsOf(UUID roleId) {
        return callAs(tenant, () -> transaction.execute(status -> {
            long rows = 0;
            for (String table : List.of("app_permissions", "contact_permissions", "company_permissions",
                    "deal_permissions")) {
                rows += jdbc.sql("select count(*) from app." + table + " where role_id = ?")
                        .param(roleId).query(Long.class).single();
            }
            return rows;
        }));
    }

    @Test
    void aNewRoleHasOneRowInEachTableAndNothingGranted() {
        RoleResponse created = create(role("Sales"));

        assertThat(rowsOf(created.id())).isEqualTo(4);
        assertThat(created.isAdmin()).isFalse();
        assertThat(created.app()).isEqualTo(AppPermissions.NONE);
        assertThat(created.entities()).containsOnlyKeys(EntityType.values());
        assertThat(created.entities().get(EntityType.CONTACT).fields())
                .hasSize(10)
                .containsEntry("firstName", new FieldAccess(false, false));
    }

    @Test
    void theDocumentComesBackAsWritten() {
        AppPermissions app = new AppPermissions(true, false, false, true, false);
        EntityPermissions contact = new EntityPermissions(true, true, true, false, Map.of(
                "firstName", new FieldAccess(true, true), "lastName", new FieldAccess(true, true),
                "email", new FieldAccess(true, false)));
        RoleResponse created = create(new RoleRequest("  Sales  ", app,
                role("x", Map.of(EntityType.CONTACT, contact)).entities()));

        RoleResponse read = get(created.id());

        assertThat(read.name()).isEqualTo("Sales");
        assertThat(read.app()).isEqualTo(app);
        EntityPermissions stored = read.entities().get(EntityType.CONTACT);
        assertThat(stored.canCreate()).isTrue();
        assertThat(stored.canDelete()).isFalse();
        assertThat(stored.fields()).containsEntry("email", new FieldAccess(true, false))
                .containsEntry("phone", new FieldAccess(false, false));
    }

    @Test
    void aKeyIsStoredThroughTheCatalogInAnySpelling() {
        EntityPermissions contact = new EntityPermissions(true, false, false, false,
                Map.of("EMAIL", new FieldAccess(true, false)));

        RoleResponse created = create(role("Sales", Map.of(EntityType.CONTACT, contact)));

        assertThat(created.entities().get(EntityType.CONTACT).fields())
                .containsEntry("email", new FieldAccess(true, false));
    }

    @Test
    void aFieldShowsUnderItsKeyAndNeverItsSlot() {
        CustomFieldDefinition field = customField("region");
        EntityPermissions contact = new EntityPermissions(true, false, false, false,
                Map.of("region", new FieldAccess(true, false)));

        RoleResponse created = create(role("Sales", Map.of(EntityType.CONTACT, contact)));

        assertThat(field.getColumnName()).startsWith("cf_text_");
        assertThat(created.entities().get(EntityType.CONTACT).fields())
                .containsEntry("region", new FieldAccess(true, false))
                .doesNotContainKey(field.getColumnName());
    }

    @Test
    void aFieldLeftOutOfAPutKeepsItsStoredBits() {
        EntityPermissions contact = new EntityPermissions(true, false, false, false,
                Map.of("email", new FieldAccess(true, true), "phone", new FieldAccess(true, false)));
        RoleResponse created = create(role("Sales", Map.of(EntityType.CONTACT, contact)));

        EntityPermissions partial = new EntityPermissions(true, false, false, false,
                Map.of("phone", new FieldAccess(false, false)));
        RoleResponse replaced = replace(created.id(), role("Sales", Map.of(EntityType.CONTACT, partial)));

        assertThat(replaced.entities().get(EntityType.CONTACT).fields())
                .containsEntry("email", new FieldAccess(true, true))
                .containsEntry("phone", new FieldAccess(false, false));
    }

    @Test
    void anArchivedFieldLeavesTheDocumentAndComesBackUnknown() {
        CustomFieldDefinition field = customField("retired");
        runAs(tenant, () -> customFields.archive(field.getId()));
        EntityPermissions contact = new EntityPermissions(true, false, false, false,
                Map.of("retired", new FieldAccess(true, false)));

        assertThat(create(role("Sales")).entities().get(EntityType.CONTACT).fields()).doesNotContainKey("retired");
        assertThatThrownBy(() -> create(role("Other", Map.of(EntityType.CONTACT, contact))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void namesClashCaseInsensitively() {
        create(role("Sales"));

        assertThatThrownBy(() -> create(role("SALES")))
                .isInstanceOf(RoleNameTakenException.class)
                .hasMessageContaining("SALES");
    }

    @Test
    void aRenameIntoAnotherRolesNameClashes() {
        create(role("Sales"));
        RoleResponse other = create(role("Support"));

        assertThatThrownBy(() -> replace(other.id(), role("sales"))).isInstanceOf(RoleNameTakenException.class);
    }

    @Test
    void savingARoleUnderItsOwnNameIsNoClash() {
        RoleResponse created = create(role("Sales"));

        assertThat(replace(created.id(), role("Sales")).name()).isEqualTo("Sales");
    }

    @Test
    void theAdministratorCantBeChangedOrDeleted() {
        UUID administrator = administratorId();

        assertThatThrownBy(() -> replace(administrator, role("Boss")))
                .isInstanceOf(AdministratorRoleLockedException.class);
        assertThatThrownBy(() -> runAs(tenant, () -> roles.delete(administrator)))
                .isInstanceOf(AdministratorRoleLockedException.class);
        assertThat(get(administrator).name()).isEqualTo("Administrator");
    }

    @Test
    void aFreeRoleGoesWithItsRows() {
        RoleResponse created = create(role("Sales"));

        runAs(tenant, () -> roles.delete(created.id()));

        assertThat(rowsOf(created.id())).isZero();
        assertThatThrownBy(() -> get(created.id())).isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void theListIsByNameRegardlessOfCaseWithHolderCounts() {
        create(role("banana"));
        create(role("Apple"));

        assertThat(callAs(tenant, () -> roles.list()))
                .extracting(RoleSummaryResponse::name, RoleSummaryResponse::userCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Administrator", 1L),
                        org.assertj.core.groups.Tuple.tuple("Apple", 0L),
                        org.assertj.core.groups.Tuple.tuple("banana", 0L),
                        org.assertj.core.groups.Tuple.tuple("Standard user", 0L));
    }
}
