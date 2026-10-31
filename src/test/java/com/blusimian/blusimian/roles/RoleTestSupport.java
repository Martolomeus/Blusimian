package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.roles.dto.AppPermissions;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import com.blusimian.blusimian.roles.dto.RoleRequest;
import com.blusimian.blusimian.tenants.NewTenant;
import com.blusimian.blusimian.tenants.TenantProvisioningService;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Building role documents and fresh tenants for the roles tests. */
final class RoleTestSupport {

    static final String PASSWORD = "roles-password";

    private RoleTestSupport() {
    }

    /** A fresh tenant: integration tests provision their own rather than rolling back. */
    static TenantId provisionTenant(TenantProvisioningService provisioning) {
        TenantId id = new TenantId(UUID.randomUUID());
        provisioning.provision(new NewTenant(id, "Roles test", "roles-" + id.value(), "First user", PASSWORD));
        return id;
    }

    static EntityPermissions closed() {
        return new EntityPermissions(false, false, false, false, Map.of());
    }

    /** Create and Read, and both bits on the given fields. */
    static EntityPermissions creating(String... keys) {
        Map<String, FieldAccess> fields = new HashMap<>();
        for (String key : keys) {
            fields.put(key, new FieldAccess(true, true));
        }
        return new EntityPermissions(true, true, false, false, fields);
    }

    /** Nothing granted on any entity, except the given overrides. */
    static RoleRequest role(String name, Map<EntityType, EntityPermissions> overrides) {
        Map<EntityType, EntityPermissions> entities = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.values()) {
            entities.put(type, overrides.getOrDefault(type, closed()));
        }
        return new RoleRequest(name, AppPermissions.NONE, entities);
    }

    static RoleRequest role(String name) {
        return role(name, Map.of());
    }
}
