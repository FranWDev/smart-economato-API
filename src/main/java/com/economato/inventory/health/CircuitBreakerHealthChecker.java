package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Active health checker that periodically tests systems when circuit breakers are OPEN.
 * When a system recovers, it manually closes the circuit breaker and triggers recovery notification.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class CircuitBreakerHealthChecker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaTemplate<String, ?> kafkaTemplate;

    /**
     * Check database health every 10 seconds if circuit breaker is OPEN.
     */
    @Scheduled(fixedDelay = 10000)
    public void checkDatabaseHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.debug("DB Circuit Breaker is OPEN. Testing database connection...");
            
            try (Connection conn = dataSource.getConnection()) {
                // Test connection with simple query
                if (conn.isValid(5)) {
                    log.info("Database health check PASSED. Closing circuit breaker.");
                    circuitBreaker.transitionToClosedState();
                }
            } catch (Exception e) {
                log.warn("Database health check FAILED: {}", e.getMessage());
            }
        }
    }

    /**
     * Check Redis health every 15 seconds if circuit breaker is OPEN.
     */
    @Scheduled(fixedDelay = 15000)
    public void checkRedisHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.debug("Redis Circuit Breaker is OPEN. Testing Redis connection...");
            
            try {
                // Test Redis connection with ping
                redisConnectionFactory.getConnection().ping();
                log.info("Redis health check PASSED. Closing circuit breaker.");
                circuitBreaker.transitionToClosedState();
            } catch (Exception e) {
                log.warn("Redis health check FAILED: {}", e.getMessage());
            }
        }
    }

    /**
     * Check Kafka health every 15 seconds if circuit breaker is OPEN.
     */
    @Scheduled(fixedDelay = 15000)
    public void checkKafkaHealth() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.debug("Kafka Circuit Breaker is OPEN. Testing Kafka connection...");
            
            try {
                // Test Kafka connection by checking metrics (lightweight operation)
                kafkaTemplate.metrics();
                log.info("Kafka health check PASSED. Closing circuit breaker.");
                circuitBreaker.transitionToClosedState();
            } catch (Exception e) {
                log.warn("Kafka health check FAILED: {}", e.getMessage());
            }
        }
    }
}
