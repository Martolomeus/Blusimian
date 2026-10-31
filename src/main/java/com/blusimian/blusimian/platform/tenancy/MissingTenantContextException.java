package com.blusimian.blusimian.platform.tenancy;

/** Tenant-scoped work was started with no tenant bound: a bug, never a request error. */
public class MissingTenantContextException extends IllegalStateException {

    public MissingTenantContextException() {
        super("No tenant bound to the current call (TenantContext)");
    }
}
