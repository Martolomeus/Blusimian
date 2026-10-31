package com.blusimian.blusimian.tenants;

import com.blusimian.blusimian.platform.tenancy.TenantId;

/** What provisioning needs: the new tenant's id and name, and its first user. */
public record NewTenant(TenantId id, String name,
                        String adminUsername, String adminDisplayName, String adminPassword) {
}
