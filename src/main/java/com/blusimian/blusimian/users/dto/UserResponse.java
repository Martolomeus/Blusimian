package com.blusimian.blusimian.users.dto;

import com.blusimian.blusimian.users.User;

import java.time.Instant;
import java.util.UUID;

/** A user as the API shows it: never the password hash. */
public record UserResponse(
        UUID id,
        String username,
        String displayName,
        boolean enabled,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.isEnabled(), user.getCreatedAt());
    }
}
