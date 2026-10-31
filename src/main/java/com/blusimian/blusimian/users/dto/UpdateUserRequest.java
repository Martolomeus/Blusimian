package com.blusimian.blusimian.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/users/{id}. username is write-once, and enabled is read-only for now. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String displayName
) {
}
