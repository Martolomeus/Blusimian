package com.blusimian.blusimian.tenants;

import com.blusimian.blusimian.platform.tenancy.TenantContext;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Creates a tenant by running as it: binds the new id, then inserts the tenant row, its
 * first user and every slice's defaults in one transaction, all under RLS.
 *
 * A TransactionTemplate rather than @Transactional, because the transaction has to begin
 * inside the scope that binds the new id. For the same reason this can't be called from
 * inside another tenant's transaction (TenantContext refuses the switch).
 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final UserService users;
    private final List<TenantInitializer> initializers;   // in @Order order
    private final TransactionTemplate transaction;

    public TenantProvisioningService(TenantRepository tenants, UserService users,
                                     List<TenantInitializer> initializers, TransactionTemplate transaction) {
        this.tenants = tenants;
        this.users = users;
        this.initializers = initializers;
        this.transaction = transaction;
    }

    public TenantId provision(NewTenant request) {
        return TenantContext.callAs(request.id(), () -> transaction.execute(status -> {
            tenants.save(Tenant.create(request.id(), request.name()));
            users.create(request.adminUsername(), request.adminDisplayName(), request.adminPassword());
            initializers.forEach(initializer -> initializer.initialize(request.id()));
            return request.id();
        }));
    }
}
