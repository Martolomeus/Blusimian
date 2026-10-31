package com.blusimian.blusimian.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/users. The user always joins the caller's tenant. */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank String password
) {
}
