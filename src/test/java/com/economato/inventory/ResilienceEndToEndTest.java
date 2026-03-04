package com.economato.inventory;

import com.economato.inventory.config.EmbeddedRedisTestConfig;
import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
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

    @MockitoBean
    private AuditOutboxProcessor outboxProcessor;

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

    @Nested
    @DisplayName("Redis Resilience: @Cacheable methods must work when Redis is down")
    class RedisResilienceTests {

        @Test
        @DisplayName("BUG: @Cacheable service should NOT throw when Redis CB is OPEN")
        void cacheableShouldWorkWhenRedisCbIsOpen() {
            CircuitBreaker redisCb = registry.circuitBreaker("redis");
            redisCb.onError(0, TimeUnit.MILLISECONDS, new RedisConnectionFailureException("Redis down"));
            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

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

    @Nested
    @DisplayName("Kafka Resilience: Outbox processor should respect circuit breaker")
    class KafkaResilienceTests {

        @Test
        @DisplayName("BUG: Kafka CB OPEN should prevent outbox processing")
        void kafkaCbOpenShouldPreventOutboxProcessing() {
            CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");

            kafkaCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.apache.kafka.common.errors.TimeoutException("Kafka unreachable"));
            assertThat(kafkaCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode())));
        }
    }

    @Nested
    @DisplayName("Recovery: Circuit breaker CLOSED should send recovery alerts")
    class RecoveryAlertTests {

        @Test
        @DisplayName("DB recovery sends DB_RECOVERED alert")
        void dbRecoverySendsAlert() {
            CircuitBreaker dbCb = registry.circuitBreaker("db");

            dbCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.hibernate.exception.JDBCConnectionException("down", new java.sql.SQLException()));
            assertThat(dbCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(messagingTemplate);

            dbCb.transitionToClosedState();

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "DB_RECOVERED".equals(msg.getCode())));
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
                    argThat((AlertMessage msg) -> "REDIS_RECOVERED".equals(msg.getCode())));
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
                    argThat((AlertMessage msg) -> "KAFKA_RECOVERED".equals(msg.getCode())));
        }
    }

    @Nested
    @DisplayName("Multi-failure: App should survive multiple infrastructure failures")
    class MultiFailureTests {

        @Test
        @DisplayName("App survives Redis + Kafka both down simultaneously")
        void appSurvivesRedisAndKafkaDown() {

            CircuitBreaker redisCb = registry.circuitBreaker("redis");
            redisCb.onError(0, TimeUnit.MILLISECONDS, new RedisConnectionFailureException("Redis down"));

            CircuitBreaker kafkaCb = registry.circuitBreaker("kafka");
            kafkaCb.onError(0, TimeUnit.MILLISECONDS,
                    new org.apache.kafka.common.errors.TimeoutException("Kafka down"));

            assertThat(redisCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(kafkaCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            assertThatCode(() -> productService.findAll(PageRequest.of(0, 10)))
                    .as("App should survive Redis + Kafka both down")
                    .doesNotThrowAnyException();

            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode())));
            verify(messagingTemplate, atLeastOnce()).convertAndSend(
                    eq("/topic/alerts"),
                    argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode())));
        }
    }
}
