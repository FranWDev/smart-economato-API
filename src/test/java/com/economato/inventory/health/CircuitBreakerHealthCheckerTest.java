package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerHealthCheckerTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private DataSource dataSource;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private KafkaTemplate<String, ?> kafkaTemplate;

    @Mock
    private CircuitBreaker dbCircuitBreaker;

    @Mock
    private CircuitBreaker redisCircuitBreaker;

    @Mock
    private CircuitBreaker kafkaCircuitBreaker;

    @InjectMocks
    private CircuitBreakerHealthChecker healthChecker;

    @Test
    void testDatabaseHealthCheck_WhenCircuitIsOpen_AndConnectionSucceeds_ShouldCloseCircuit() throws SQLException {
        // Given: DB circuit is OPEN and connection is healthy
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        Connection mockConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);

        // When: Health check runs
        healthChecker.checkDatabaseHealth();

        // Then: Circuit breaker should be closed
        verify(dbCircuitBreaker).transitionToClosedState();
        verify(mockConnection).close();
    }

    @Test
    void testDatabaseHealthCheck_WhenCircuitIsOpen_AndConnectionFails_ShouldStayOpen() throws SQLException {
        // Given: DB circuit is OPEN and connection fails
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // When: Health check runs
        healthChecker.checkDatabaseHealth();

        // Then: Circuit breaker should NOT be closed
        verify(dbCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testDatabaseHealthCheck_WhenCircuitIsClosed_ShouldNotTest() throws SQLException {
        // Given: DB circuit is CLOSED
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When: Health check runs
        healthChecker.checkDatabaseHealth();

        // Then: Should not attempt connection
        verify(dataSource, never()).getConnection();
        verify(dbCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsOpen_AndPingSucceeds_ShouldCloseCircuit() {
        // Given: Redis circuit is OPEN and ping succeeds
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        RedisConnection mockRedisConnection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(mockRedisConnection);
        when(mockRedisConnection.ping()).thenReturn("PONG");

        // When: Health check runs
        healthChecker.checkRedisHealth();

        // Then: Circuit breaker should be closed
        verify(redisCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsOpen_AndPingFails_ShouldStayOpen() {
        // Given: Redis circuit is OPEN and ping fails
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Redis connection failed"));

        // When: Health check runs
        healthChecker.checkRedisHealth();

        // Then: Circuit breaker should NOT be closed
        verify(redisCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsClosed_ShouldNotTest() {
        // Given: Redis circuit is CLOSED
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When: Health check runs
        healthChecker.checkRedisHealth();

        // Then: Should not attempt connection
        verify(redisConnectionFactory, never()).getConnection();
        verify(redisCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsOpen_AndMetricsSucceed_ShouldCloseCircuit() {
        // Given: Kafka circuit is OPEN and metrics succeed
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        doReturn(Collections.emptyMap()).when(kafkaTemplate).metrics();

        // When: Health check runs
        healthChecker.checkKafkaHealth();

        // Then: Circuit breaker should be closed
        verify(kafkaCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsOpen_AndMetricsFail_ShouldStayOpen() {
        // Given: Kafka circuit is OPEN and metrics fail
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(kafkaTemplate.metrics()).thenThrow(new RuntimeException("Kafka unavailable"));

        // When: Health check runs
        healthChecker.checkKafkaHealth();

        // Then: Circuit breaker should NOT be closed
        verify(kafkaCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsClosed_ShouldNotTest() {
        // Given: Kafka circuit is CLOSED
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When: Health check runs
        healthChecker.checkKafkaHealth();

        // Then: Should not attempt connection
        verify(kafkaTemplate, never()).metrics();
        verify(kafkaCircuitBreaker, never()).transitionToClosedState();
    }
}
