package com.blusimian.blusimian.roles.dto;

import com.blusimian.blusimian.entityModel.EntityType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The role document. Fields go by key, never by slot: storage isn't part of the contract. */
public record RoleResponse(
        UUID id,
        String name,
        boolean isAdmin,
        AppPermissions app,
        Map<EntityType, EntityPermissions> entities,
        Instant createdAt,
        Instant updatedAt
) {
}
