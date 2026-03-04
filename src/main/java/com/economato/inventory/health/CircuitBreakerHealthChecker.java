package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Periodically tests systems when circuit breakers are OPEN and closes them upon recovery.
 */
@Slf4j
@Service
@Profile("!test & !resilience-test")
@RequiredArgsConstructor
public class CircuitBreakerHealthChecker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaTemplate<String, ?> kafkaTemplate;

    @Scheduled(fixedDelay = 10000)
    public void checkDatabaseHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    log.info("Database recovered, closing circuit breaker");
                    circuitBreaker.transitionToClosedState();
                }
            } catch (Exception e) {
                log.debug("Database still unavailable: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkRedisHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            try {
                var conn = redisConnectionFactory.getConnection();
                try {
                    conn.ping();
                } finally {
                    conn.close();
                }
                log.info("Redis recovered, closing circuit breaker");
                circuitBreaker.transitionToClosedState();
            } catch (Exception e) {
                log.debug("Redis still unavailable: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkKafkaHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            AdminClient adminClient = null;
            try {
                ProducerFactory<String, ?> producerFactory = kafkaTemplate.getProducerFactory();
                Map<String, Object> adminConfigs = new HashMap<>(producerFactory.getConfigurationProperties());
                adminConfigs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
                adminConfigs.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 9000);
                
                adminClient = AdminClient.create(adminConfigs);
                adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
                
                log.info("Kafka recovered, closing circuit breaker");
                circuitBreaker.transitionToClosedState();
            } catch (Exception e) {
                log.debug("Kafka still unavailable: {}", e.getMessage());
            } finally {
                if (adminClient != null) {
                    adminClient.close();
                }
            }
        }
    }
}
