package com.blusimian.blusimian.tenants;

import com.blusimian.blusimian.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current tenant. */
@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Tenant current() {
        return repository.findById(TenantContext.require().value())
                .orElseThrow(() -> new IllegalStateException("The current tenant has no row"));
    }
}
