package com.blusimian.blusimian.auth.dto;

import java.util.UUID;

/** Body of GET /api/auth/me: who is logged in, and in which tenant. */
public record CurrentUserResponse(
        UUID userId,
        String username,
        String displayName,
        TenantSummary tenant
) {
    public record TenantSummary(UUID id, String name) {
    }
}
