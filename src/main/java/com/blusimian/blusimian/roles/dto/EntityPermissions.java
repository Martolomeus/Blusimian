package com.blusimian.blusimian.roles.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** One entity in the role document: its CRUD layer, then its field layer, by field key. */
public record EntityPermissions(
        boolean canRead,
        boolean canCreate,
        boolean canEdit,
        boolean canDelete,
        @NotNull Map<String, @NotNull FieldAccess> fields
) {
}
