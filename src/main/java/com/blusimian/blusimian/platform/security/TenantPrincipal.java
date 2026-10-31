package com.blusimian.blusimian.platform.security;

import com.blusimian.blusimian.platform.tenancy.TenantId;

import java.util.UUID;

/**
 * The seam between authentication and tenancy: tenancy reads only this. A replacement auth
 * mechanism (OIDC, JWT) only has to produce an Authentication whose principal implements it.
 */
public interface TenantPrincipal {

    TenantId tenantId();

    UUID userId();
}
