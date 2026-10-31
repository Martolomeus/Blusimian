package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.AppPermissions;
import com.blusimian.blusimian.tenants.TenantInitializer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Gives a new tenant Administrator and Standard user, and makes its first user an Administrator. */
@Component
class RolesInitializer implements TenantInitializer {

    static final String ADMINISTRATOR = "Administrator";
    static final String STANDARD_USER = "Standard user";

    private final RoleService roles;
    private final RoleAssignmentService assignments;

    RolesInitializer(RoleService roles, RoleAssignmentService assignments) {
        this.roles = roles;
        this.assignments = assignments;
    }

    @Override
    public void initialize(TenantId tenant, UUID firstUserId) {
        Role administrator = roles.createWithFullRecordAccess(Role.createAdministrator(ADMINISTRATOR), AppPermissions.ALL);
        roles.createWithFullRecordAccess(Role.create(STANDARD_USER), AppPermissions.NONE);
        assignments.assign(firstUserId, administrator.getId());
    }
}
