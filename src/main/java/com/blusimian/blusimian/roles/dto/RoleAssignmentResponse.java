package com.blusimian.blusimian.roles.dto;

import com.blusimian.blusimian.roles.RoleAssignment;

import java.util.UUID;

/** A user holding a role. */
public record RoleAssignmentResponse(UUID userId, UUID roleId) {

    public static RoleAssignmentResponse from(RoleAssignment assignment) {
        return new RoleAssignmentResponse(assignment.getUserId(), assignment.getRoleId());
    }
}
