package com.blusimian.blusimian.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/auth/login. No tenant: usernames are unique across tenants. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
