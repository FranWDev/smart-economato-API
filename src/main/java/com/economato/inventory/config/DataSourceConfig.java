package com.economato.inventory.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@Profile("!test & !resilience-test")
public class DataSourceConfig {

    @Value("${spring.datasource.hikari.connection-timeout:2000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.validation-timeout:1000}")
    private long validationTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.maximum-pool-size:25}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.data-source-properties.socketTimeout:5}")
    private int socketTimeoutSeconds;

    @Value("${spring.datasource.hikari.data-source-properties.connectTimeout:3}")
    private int connectTimeoutSeconds;

    @Bean
    @ConfigurationProperties("spring.datasource.writer")
    public DataSource writerDataSource() {
        HikariDataSource writer = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        configureHikari(writer, "writer-pool");
        return writer;
    }

    @Bean
    @ConfigurationProperties("spring.datasource.reader")
    public DataSource readerDataSource() {
        HikariDataSource reader = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        configureHikari(reader, "reader-pool");
        return reader;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("writerDataSource") DataSource writer,
            @Qualifier("readerDataSource") DataSource reader) {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITER, writer);
        targetDataSources.put(DataSourceType.READER, reader);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writer);

        return routingDataSource;
    }

    private void configureHikari(HikariDataSource dataSource, String poolName) {
        dataSource.setPoolName(poolName);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setValidationTimeout(validationTimeout);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);

        dataSource.addDataSourceProperty("socketTimeout", socketTimeoutSeconds);
        dataSource.addDataSourceProperty("connectTimeout", connectTimeoutSeconds);
        dataSource.addDataSourceProperty("prepStmtCacheSize", 250);
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
        dataSource.addDataSourceProperty("cachePrepStmts", true);
        dataSource.addDataSourceProperty("useServerPrepStmts", true);
    }
}
