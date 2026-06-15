package com.cuifeng.backend.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration
@Profile("mysql")
public class DatabaseConfig {
    @Bean
    public DataSource takeoutDataSource(
            @Value("${app.datasource.url}") String jdbcUrl,
            @Value("${app.datasource.username}") String username,
            @Value("${app.datasource.password}") String password,
            @Value("${app.datasource.maximum-pool-size:12}") int maximumPoolSize) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setPoolName("takeout-mysql");
        dataSource.setAutoCommit(true);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource takeoutDataSource) {
        return new JdbcTemplate(takeoutDataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(DataSource takeoutDataSource) {
        return new TransactionTemplate(new JdbcTransactionManager(takeoutDataSource));
    }
}
