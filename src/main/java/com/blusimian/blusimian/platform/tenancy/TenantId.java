package com.blusimian.blusimian.platform.tenancy;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * A tenant's id. Its own type so a method taking a tenant can't be handed a record id.
 * Serializable because the session stores it inside the principal.
 */
public record TenantId(UUID value) implements Serializable {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
