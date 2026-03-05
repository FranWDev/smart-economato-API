package com.economato.inventory.config;

import com.economato.inventory.event.WebSocketConnectedEvent;
import com.economato.inventory.kafka.producer.AuditOutboxProcessor;
import com.economato.inventory.security.JwtUtils;
import com.economato.inventory.service.CustomUserDetailsService;
import com.economato.inventory.service.notification.AlertMessage;
import com.economato.inventory.service.notification.WebSocketNotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests that verify new WebSocket connections receive notifications
 * about open circuit breakers.
 */
@SpringBootTest
@ActiveProfiles("test")
public class WebSocketCircuitBreakerNotificationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private WebSocketNotificationService webSocketNotificationService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private AuditOutboxProcessor outboxProcessor;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers to CLOSED state
        circuitBreakerRegistry.circuitBreaker("db").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("redis").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("kafka").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("replica").transitionToClosedState();
        
        // Reset mock interactions
        reset(messagingTemplate);
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenDbIsOpen_SendsDbFailureAlert() {
        // Arrange: Open the DB circuit breaker
        CircuitBreaker dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        RuntimeException fakeException = new org.hibernate.exception.JDBCConnectionException(
                "DB Connection Refused",
                new java.sql.SQLException());
        dbCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, fakeException);
        
        assert(dbCircuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify the user receives DB_FAILURE notification
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "DB_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenRedisIsOpen_SendsRedisFailureAlert() {
        // Arrange: Open the Redis circuit breaker
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        RuntimeException fakeException = new org.springframework.data.redis.RedisConnectionFailureException("Redis is down");
        redisCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, fakeException);
        
        assert(redisCircuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify the user receives REDIS_FAILURE notification
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenKafkaIsOpen_SendsKafkaFailureAlert() {
        // Arrange: Open the Kafka circuit breaker
        CircuitBreaker kafkaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        RuntimeException fakeException = new org.apache.kafka.common.errors.TimeoutException("Kafka broker unreachable");
        kafkaCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, fakeException);
        
        assert(kafkaCircuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify the user receives KAFKA_FAILURE notification
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "KAFKA_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenReplicaIsOpen_SendsReplicaFailureAlert() {
        // Arrange: Open the Replica circuit breaker
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
        RuntimeException fakeException = new org.hibernate.exception.JDBCConnectionException(
                "Replica Connection Refused",
                new java.sql.SQLException());
        replicaCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, fakeException);
        
        assert(replicaCircuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify the user receives REPLICA_FAILURE notification
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "REPLICA_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenMultipleCircuitBreakersAreOpen_SendsMultipleAlerts() {
        // Arrange: Open both DB and Redis circuit breakers
        CircuitBreaker dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        
        RuntimeException dbException = new org.hibernate.exception.JDBCConnectionException(
                "DB Connection Refused",
                new java.sql.SQLException());
        RuntimeException redisException = new org.springframework.data.redis.RedisConnectionFailureException("Redis is down");
        
        dbCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, dbException);
        redisCircuitBreaker.onError(0, TimeUnit.MILLISECONDS, redisException);
        
        assert(dbCircuitBreaker.getState() == CircuitBreaker.State.OPEN);
        assert(redisCircuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify the user receives both DB_FAILURE and REDIS_FAILURE notifications
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "DB_FAILURE".equals(msg.getCode()))
        );
        
        verify(messagingTemplate).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                argThat((AlertMessage msg) -> "REDIS_FAILURE".equals(msg.getCode()))
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenAllCircuitBreakersAreClosed_SendsNoAlerts() {
        // Arrange: All circuit breakers are closed (done in setUp)
        CircuitBreaker dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        CircuitBreaker kafkaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
        
        assert(dbCircuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        assert(redisCircuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        assert(kafkaCircuitBreaker.getState() == CircuitBreaker.State.CLOSED);
        assert(replicaCircuitBreaker.getState() == CircuitBreaker.State.CLOSED);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify NO alerts are sent to the user
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                any(AlertMessage.class)
        );
    }

    @Test
    void testNotifyOpenCircuitBreakers_WhenCircuitBreakerIsHalfOpen_SendsNoAlert() {
        // Arrange: Transition Redis circuit breaker to HALF_OPEN
        CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        redisCircuitBreaker.transitionToOpenState();
        redisCircuitBreaker.transitionToHalfOpenState();
        
        assert(redisCircuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

        // Act: Simulate a new WebSocket connection via event
        String username = "testuser";
        webSocketNotificationService.handleWebSocketConnected(new WebSocketConnectedEvent(username));

        // Assert: Verify NO alerts are sent (only OPEN state should trigger notifications)
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq(username),
                eq("/queue/alerts"),
                any(AlertMessage.class)
        );
    }
}
