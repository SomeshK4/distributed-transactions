package com.example.demoxa.config;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.hibernate.SpringJtaPlatform;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.jta.JtaTransactionManager;

/**
 * Manual JTA wiring for Atomikos.
 *
 * <p>Spring Boot 4 dropped {@code TransactionManagerCustomizers}, which
 * Atomikos's {@code transactions-spring-boot[3]-starter} 6.0.x still
 * references — so its auto-configuration fails with
 * {@code NoClassDefFoundError} at startup. We bypass it (by excluding
 * {@code transactions-spring-boot3} in the POM) and register the three JTA
 * beans ourselves.
 */
@Configuration
public class JtaConfig {

    /**
     * Atomikos's TransactionManager implementation. Atomikos starts its core
     * via {@code init()} and shuts it down via {@code close()}. The
     * {@code forceShutdown=false} flag lets in-flight XA transactions finish
     * cleanly on JVM exit.
     */
    @Bean(initMethod = "init", destroyMethod = "close")
    public UserTransactionManager atomikosTransactionManager() {
        UserTransactionManager tm = new UserTransactionManager();
        tm.setForceShutdown(false);
        return tm;
    }

    @Bean
    public UserTransaction atomikosUserTransaction() throws SystemException {
        UserTransactionImp ut = new UserTransactionImp();
        ut.setTransactionTimeout(300);
        return ut;
    }

    /**
     * Spring's {@link JtaTransactionManager} — what {@code @Transactional}
     * ultimately delegates to. It enlists every registered XA resource
     * (Postgres datasource + Artemis connection factory) into a single
     * global transaction.
     */
    @Bean
    @DependsOn("atomikosTransactionManager")
    public JtaTransactionManager transactionManager(
            @Qualifier("atomikosUserTransaction") UserTransaction userTransaction,
            @Qualifier("atomikosTransactionManager") TransactionManager transactionManager) {

        JtaTransactionManager jta =
                new JtaTransactionManager(userTransaction, transactionManager);
        jta.setAllowCustomIsolationLevels(true);
        return jta;
    }

    @Bean
    public HibernatePropertiesCustomizer jtaPlatformCustomizer(
            JtaTransactionManager jtaTransactionManager) {
        return properties -> properties.put(
                AvailableSettings.JTA_PLATFORM,
                new SpringJtaPlatform(jtaTransactionManager));
    }
}

