package com.blusimian.blusimian.platform.tenancy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
class TenancyConfiguration {

    // Mirrors Boot's own bean, which then backs off: the no-arg constructor finds the
    // EntityManagerFactory itself, and Boot's customizers (spring.transaction.*,
    // TransactionExecutionListener beans) still apply.
    @Bean
    JpaTransactionManager transactionManager(ObjectProvider<TransactionManagerCustomizers> customizers) {
        JpaTransactionManager transactionManager = new TenantAwareJpaTransactionManager();
        customizers.ifAvailable(c -> c.customize(transactionManager));
        return transactionManager;
    }
}
