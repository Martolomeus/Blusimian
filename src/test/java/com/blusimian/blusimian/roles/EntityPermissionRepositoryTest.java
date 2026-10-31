package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JdbcClient path against the dev database, as blusimian_app, under RLS. */
@SpringBootTest
class EntityPermissionRepositoryTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleService roles;
    @Autowired
    EntityPermissionRepository permissions;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate transaction;

    TenantId tenant;
    TenantId other;

    @BeforeEach
    void provisionTwoTenants() {
        tenant = provisionTenant(provisioning);
        other = provisionTenant(provisioning);
    }

    /** A role row with no permission rows, so the repository's own insert is what's tested. */
    private UUID bareRole(TenantId owner) {
        return callAs(owner, () -> transaction.execute(status ->
                jdbc.sql("insert into app.roles (name) values (:name) returning id")
                        .param("name", "Bare " + UUID.randomUUID())
                        .query(UUID.class)
                        .single()));
    }

    private Map<String, Object> row(TenantId owner, EntityType type, UUID roleId) {
        return callAs(owner, () -> transaction.execute(status -> permissions.find(type, roleId)));
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void aNewRowIsClosedInEveryColumn(EntityType type) {
        UUID roleId = bareRole(tenant);

        runAs(tenant, () -> transaction.executeWithoutResult(status -> permissions.insert(type, roleId)));

        Map<String, Object> row = row(tenant, type, roleId);
        assertThat(row).hasSize(type == EntityType.DEAL ? 225 : 239);
        assertThat(row.entrySet())
                .filteredOn(column -> column.getKey().endsWith("_read") || column.getKey().endsWith("_edit")
                        || column.getKey().startsWith("can_"))
                .hasSize(type == EntityType.DEAL ? 220 : 234)
                .allSatisfy(column -> assertThat(column.getValue()).isEqualTo(false));
    }

    @Test
    void anUpdateChangesOnlyTheColumnsItNames() {
        UUID roleId = bareRole(tenant);
        runAs(tenant, () -> transaction.executeWithoutResult(status -> {
            permissions.insert(EntityType.CONTACT, roleId);
            permissions.update(EntityType.CONTACT, roleId, Map.of("email_read", true, "can_read", true));
        }));

        Map<String, Object> row = row(tenant, EntityType.CONTACT, roleId);
        assertThat(row.get("email_read")).isEqualTo(true);
        assertThat(row.get("can_read")).isEqualTo(true);
        assertThat(row.get("email_edit")).isEqualTo(false);
        assertThat(row.get("phone_read")).isEqualTo(false);
    }

    @Test
    void anotherTenantsRowCantBeReadOrUpdated() {
        UUID roleId = bareRole(tenant);
        runAs(tenant, () -> transaction.executeWithoutResult(status -> permissions.insert(EntityType.DEAL, roleId)));

        assertThatThrownBy(() -> row(other, EntityType.DEAL, roleId))
                .isInstanceOf(EmptyResultDataAccessException.class);
        runAs(other, () -> transaction.executeWithoutResult(status ->
                permissions.update(EntityType.DEAL, roleId, Map.of("can_read", true))));

        assertThat(row(tenant, EntityType.DEAL, roleId).get("can_read")).isEqualTo(false);
    }

    @Test
    void creatingARoleFlushesItBeforeThePermissionRows() {
        // Hibernate holds the role's INSERT, so without the flush the rows' FK fails
        RoleResponse created = callAs(tenant, () -> roles.create(role("Flushed")));

        assertThat(row(tenant, EntityType.COMPANY, created.id())).isNotEmpty();
    }
}
