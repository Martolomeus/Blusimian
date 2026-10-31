package com.blusimian.blusimian.platform.tenancy;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * The tenant of the current call, as a ScopedValue: bound for the extent of callAs/runAs,
 * immutable inside it, and not inherited by other threads. TenantBindingJpaDialect hands it
 * to Postgres when a transaction begins.
 */
public final class TenantContext {

    private static final ScopedValue<TenantId> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static Optional<TenantId> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static TenantId require() {
        if (!CURRENT.isBound()) {
            throw new MissingTenantContextException();
        }
        return CURRENT.get();
    }

    public static <T, X extends Throwable> T callAs(TenantId tenant,
                                                    ScopedValue.CallableOp<? extends T, X> op) throws X {
        refuseSwitchInsideTransaction(tenant);
        return ScopedValue.where(CURRENT, tenant).call(op);
    }

    public static void runAs(TenantId tenant, Runnable op) {
        refuseSwitchInsideTransaction(tenant);
        ScopedValue.where(CURRENT, tenant).run(op);
    }

    // The tenant reaches Postgres when a transaction begins, so a new scope inside an
    // open transaction would keep reading and writing the old tenant.
    private static void refuseSwitchInsideTransaction(TenantId tenant) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !current().map(tenant::equals).orElse(false)) {
            throw new IllegalStateException("Cannot switch to tenant " + tenant + " inside an open transaction");
        }
    }
}
