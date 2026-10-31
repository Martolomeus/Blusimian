package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;

import static com.blusimian.blusimian.platform.tenancy.TenantContext.callAs;
import static com.blusimian.blusimian.platform.tenancy.TenantContext.runAs;
import static com.blusimian.blusimian.roles.RoleTestSupport.provisionTenant;
import static com.blusimian.blusimian.roles.RoleTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Driven by the providers, so ck_*_required and each field's required flag can't drift apart. */
@SpringBootTest
class RequiredFieldCheckTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    RoleService roles;
    @Autowired
    EntityPermissionRepository permissions;
    @Autowired
    StandardFieldRegistry standardFields;
    @Autowired
    TransactionTemplate transaction;

    private void update(TenantId tenant, EntityType type, RoleResponse role, Map<String, Boolean> columns) {
        runAs(tenant, () -> transaction.executeWithoutResult(status ->
                permissions.update(type, role.id(), columns)));
    }

    /** Create on, and every required field open but the one left out. */
    private Map<String, Boolean> creating(EntityType type, String closed) {
        Map<String, Boolean> columns = new HashMap<>(Map.of("can_read", true, "can_create", true));
        for (FieldDescriptor<?> field : standardFields.fieldsFor(type)) {
            String prefix = FieldPermissionCatalog.snakeCase(field.key());
            boolean open = field.required() && !field.key().equals(closed);
            columns.put(PermissionField.readColumn(prefix), open);
            columns.put(PermissionField.editColumn(prefix), open);
        }
        return columns;
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void eachRequiredFieldClosedInTurnIsRefusedWhileCreateIsOn(EntityType type) {
        TenantId tenant = provisionTenant(provisioning);
        RoleResponse role = callAs(tenant, () -> roles.create(role("Check")));

        assertThat(standardFields.fieldsFor(type)).anyMatch(FieldDescriptor::required);
        for (FieldDescriptor<?> field : standardFields.fieldsFor(type)) {
            if (field.required()) {
                assertThatThrownBy(() -> update(tenant, type, role, creating(type, field.key())))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .rootCause().hasMessageContaining("ck_" + EntityPermissionRepository.table(type) + "_required");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void onlyRequiredFieldsAreDemanded(EntityType type) {
        TenantId tenant = provisionTenant(provisioning);
        RoleResponse role = callAs(tenant, () -> roles.create(role("Check")));

        // every optional field closed: a CHECK naming one of them would refuse this
        assertThatCode(() -> update(tenant, type, role, creating(type, null))).doesNotThrowAnyException();
    }
}
