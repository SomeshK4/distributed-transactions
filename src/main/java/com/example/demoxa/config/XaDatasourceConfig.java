package com.example.demoxa.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.postgresql.xa.PGXADataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Wraps the Postgres {@link PGXADataSource} in an Atomikos XA pool so that
 * every JDBC connection acquired here automatically enlists in the active
 * JTA (global) transaction managed by {@link JtaConfig}.
 */
@Configuration
public class XaDatasourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean(initMethod = "init", destroyMethod = "close")
    @DependsOn("atomikosTransactionManager")
    @Primary
    public DataSource dataSource() {

        PGXADataSource xa = new PGXADataSource();
        xa.setUrl(url);
        xa.setUser(username);
        xa.setPassword(password);

        AtomikosDataSourceBean ds = new AtomikosDataSourceBean();
        ds.setUniqueResourceName("postgres-xa");
        ds.setXaDataSource(xa);
        ds.setMinPoolSize(1);
        ds.setMaxPoolSize(10);
        ds.setBorrowConnectionTimeout(30);
        ds.setTestQuery("SELECT 1");
        return ds;
    }
}

