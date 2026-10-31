package com.blusimian.blusimian.roles.dto;

import com.blusimian.blusimian.entityModel.EntityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Body of POST and PUT /api/roles. There is no isAdmin: only provisioning makes one. Bean
 * validation covers the shape; the rules, the name included, are RoleValidator's.
 */
public record RoleRequest(
        String name,
        @NotNull @Valid AppPermissions app,
        @NotNull Map<EntityType, @NotNull @Valid EntityPermissions> entities
) {
}
