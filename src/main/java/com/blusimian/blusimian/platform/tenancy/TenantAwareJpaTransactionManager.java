package com.blusimian.blusimian.platform.tenancy;

import org.springframework.orm.jpa.JpaTransactionManager;

/** A JpaTransactionManager whose transactions begin with the current tenant bound in Postgres. */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    // super.afterPropertiesSet() takes the dialect from the EntityManagerFactory,
    // so ours has to be installed after it.
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        setJpaDialect(new TenantBindingJpaDialect());
    }
}
