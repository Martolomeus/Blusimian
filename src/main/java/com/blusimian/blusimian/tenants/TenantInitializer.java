package com.blusimian.blusimian.tenants;

import com.blusimian.blusimian.platform.tenancy.TenantId;

import java.util.UUID;

/** Sets up a slice's defaults for a new tenant. Implemented by the slice that owns them. */
public interface TenantInitializer {

    /** Called once per new tenant, inside the provisioning transaction, with the tenant bound. */
    void initialize(TenantId tenant, UUID firstUserId);
}
