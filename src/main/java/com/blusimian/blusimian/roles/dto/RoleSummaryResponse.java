package com.blusimian.blusimian.roles.dto;

import com.blusimian.blusimian.roles.Role;

import java.util.UUID;

/** A row of the roles list. Never reads the wide permission tables. */
public record RoleSummaryResponse(UUID id, String name, boolean isAdmin, long userCount) {

    public static RoleSummaryResponse from(Role role, long userCount) {
        return new RoleSummaryResponse(role.getId(), role.getName(), role.isAdmin(), userCount);
    }
}
