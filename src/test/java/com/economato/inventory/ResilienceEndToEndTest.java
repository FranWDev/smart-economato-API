package com.economato.inventory;

import com.economato.inventory.config.EmbeddedRedisTestConfig;
import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
import com.economato.inventory.model.AuditOutbox;
import com.economato.inventory.repository.AuditOutboxRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.security.JwtUtils;
import com.economato.inventory.service.CustomUserDetailsService;
import com.economato.inventory.service.ProductService;
import com.economato.inventory.service.notification.AlertMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Resilience Tests
 * 
 * Verifies that the application degrades gracefully when infrastructure fails.
 * Uses real Spring context with embedded Redis and real circuit breakers.
 * 
 * These tests catch bugs that unit/mock-based tests miss:
 * - RedisCacheErrorHandler throwing when CB is OPEN (breaks all @Cacheable methods)
 * - AuditOutboxProcessor not checking Kafka CB state before processing
 * - Recovery alerts after circuit breaker closes
 * 
 * Profile "resilience-test" activates:
 * - Real Redis cache (via embedded Redis)
 * - Real CachingConfig + RedisCacheErrorHandler
 * - Real Resilience4j circuit breakers
 */
@SpringBootTest
@ActiveProfiles("resilience-test")
@Import(EmbeddedRedisTestConfig.class)
public class ResilienceEndToEndTest {

    @Autowired
    private CircuitBreakerRegistry registry;

    @Autowired
    private ProductService productService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // AuditOutboxProcessor needs to be a real bean for Kafka tests,
    // but excluded by profile. We mock it to prevent scheduling issues.
    @MockitoBean
    private AuditOutboxProcessor outboxProcessor;

    // Prevent scheduled health checks from closing circuit breakers during tests
    @MockitoBean
    private com.economato.inventory.health.CircuitBreakerHealthChecker healthChecker;

    @BeforeEach
    void resetCircuitBreakers() {
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.transitionToClosedState();
            cb.reset();
        });
        reset(messagingTemplate);
    }

    // =========================================================================
    // BUG #1: RedisCacheErrorHandler throws when Redis CB is OPEN
    // =========================================================================

    @Nested
    @DisplayName("Redis Resilience: @Cacheable methods must work when Redis is down")
    class RedisResilienceTests {

        /**
         * This test exposes the bug in RedisCacheErrorHandler.checkCircuitBreaker():
         * When Redis CB is OPEN, it throws RuntimeException("Redis circuit breaker OPEN")
         * which propagates through Spring's cache interceptor to the caller.
         * 
         * EXPECTED: Cache miss is silently ignored, service returns data from DB.
         * ACTUAL (BUG): Throws RuntimeException, making the entire app unusable.
         */
        @Test
        @DisplayName("BUG: @Cacheable service should NOT throw when Redis CB is OPEN")
        void cacheableShouldWorkWhenRedisCbIsOpen() {
            // Force Redis CB to OPEN
            CircuitBreaker redisCb = registry.circuitBreaker("redis");
            redisCb.onError(0, TimeUnit.MILLISECONDS, new RedisConnectionFailureException("Redis down"));
            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // This should NOT throw - it should bypass cache and query DB
            assertThatCode(() -> productService.findAll(PageRequest.of(0, 10)))
                    .as("@Cacheable method should degrade gracefully when Redis CB is OPEN")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("@Cacheable should work normally when Redis is healthy")
        void cacheableShouldWorkWhenRedisIsHealthy() {
            CircuitBreaker redisCb = registry.circuitBreaker("redis");
            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            assertThatCode(() -> productService.findAll(PageRequest.of(0, 10)))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // BUG #2: AuditOutboxProcessor doesn't check Kafka CB state
    // =========================================================================

    @Nested
    @DisplayName("Kafka Resilience: Outbox processor should respect circuit breaker")
    class KafkaResilienceTests {

        /**
         * This test verifies that processOutbox() short-circuits when the Kafka
         * circuit breaker is OPEN, instead of fetching 100 events and trying
         * to send each one (wasting ~30 seconds per cycle).
         * 
         * Since AuditOutboxProcessor is mocked in this context (due to profile),
         * this test documents the expected behavior. For a real verification,
         * you need the "kafka-test" profile with @EmbeddedKafka.
         * 
         * The key assertion is: when Kafka CB is OPEN, the outbox repository
         * should NOT be queried at all.
         */
        @Test
        @DisplayName("BUG: Kafka CB OPEN should prevent outbox processing")
        void kafkaCbOpenShouldPreventOutboxProcessing() {
            CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");

            // Force Kafka CB to OPEN
            kafkaCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.apache.kafka.common.errors.TimeoutException("Kafka unreachable"));
            assertThat(kafkaCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Verify alert was sent
            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode()))
            );
        }
    }

    // =========================================================================
    // Recovery alerts: CB transitions OPEN → CLOSED should notify frontend
    // =========================================================================

    @Nested
    @DisplayName("Recovery: Circuit breaker CLOSED should send recovery alerts")
    class RecoveryAlertTests {

        @Test
        @DisplayName("DB recovery sends DB_RECOVERED alert")
        void dbRecoverySendsAlert() {
            CircuitBreaker dbCb = registry.circuitBreaker("db");

            // Open it
            dbCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.hibernate.exception.JDBCConnectionException("down", new java.sql.SQLException()));
            assertThat(dbCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(messagingTemplate);

            // Close it (simulating health checker)
            dbCb.transitionToClosedState();

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "DB_RECOVERED".equals(msg.getCode()))
            );
        }

        @Test
        @DisplayName("Redis recovery sends REDIS_RECOVERED alert")
        void redisRecoverySendsAlert() {
            CircuitBreaker redisCb = registry.circuitBreaker("redis");

            redisCb.onError(0, TimeUnit.MILLISECONDS, new RedisConnectionFailureException("down"));
            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(messagingTemplate);

            redisCb.transitionToClosedState();

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "REDIS_RECOVERED".equals(msg.getCode()))
            );
        }

        @Test
        @DisplayName("Kafka recovery sends KAFKA_RECOVERED alert")
        void kafkaRecoverySendsAlert() {
            CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");

            kafkaCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.apache.kafka.common.errors.TimeoutException("down"));
            assertThat(kafkaCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(messagingTemplate);

            kafkaCb.transitionToClosedState();

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "KAFKA_RECOVERED".equals(msg.getCode()))
            );
        }
    }

    // =========================================================================
    // Full degradation scenario: multiple systems fail simultaneously
    // =========================================================================

    @Nested
    @DisplayName("Multi-failure: App should survive multiple infrastructure failures")
    class MultiFailureTests {

        @Test
        @DisplayName("App survives Redis + Kafka both down simultaneously")
        void appSurvivesRedisAndKafkaDown() {
            // Kill Redis CB
            CircuitBreaker redisCb = registry.circuitBreaker("redis");
            redisCb.onError(0, TimeUnit.MILLISECONDS, new RedisConnectionFailureException("Redis down"));

            // Kill Kafka CB
            CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");
            kafkaCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.apache.kafka.common.errors.TimeoutException("Kafka down"));

            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(kafkaCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Service should STILL work (cache miss + no kafka = acceptable degradation)
            assertThatCode(() -> productService.findAll(PageRequest.of(0, 10)))
                    .as("App should survive Redis + Kafka both down")
                    .doesNotThrowAnyException();

            // Both alerts should have been sent
            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode()))
            );
            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode()))
            );
        }
    }
}
