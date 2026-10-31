package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps a role with Create able to fill in a new required field: the one automatic grant. */
@Component
class RequiredFieldGrants {

    private final EntityPermissionRepository permissions;

    RequiredFieldGrants(EntityPermissionRepository permissions) {
        this.permissions = permissions;
    }

    // Synchronous on purpose: it must share the field's transaction and tenant.
    @EventListener
    void on(CustomFieldCreated field) {
        if (field.required()) {
            permissions.grantToRolesWithCreate(field.entityType(), field.columnName());
        }
    }
}
