package com.blusimian.blusimian.roles.dto;

/** One field's two bits on a role. */
public record FieldAccess(boolean read, boolean edit) {
}
