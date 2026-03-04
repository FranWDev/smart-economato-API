package com.economato.inventory.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

@TestConfiguration
@Profile("resilience-test")
public class TestDataSourceConfig {

    @Bean
    @Primary
    public DataSource writerDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb")
                .build();
    }

    @Bean(name = "readerDataSource")
    @Primary
    public DataSource readerDataSource() {
        // For tests, reader points to the same database as writer
        return writerDataSource();
    }
}
