package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
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
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        Connection mockConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(5)).thenReturn(true);

        healthChecker.checkDatabaseHealth();

        verify(dbCircuitBreaker).transitionToClosedState();
        verify(mockConnection).close();
    }

    @Test
    void testDatabaseHealthCheck_WhenCircuitIsOpen_AndConnectionFails_ShouldStayOpen() throws SQLException {
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        healthChecker.checkDatabaseHealth();

        verify(dbCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testDatabaseHealthCheck_WhenCircuitIsClosed_ShouldNotTest() throws SQLException {
        when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        healthChecker.checkDatabaseHealth();

        verify(dataSource, never()).getConnection();
        verify(dbCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsOpen_AndPingSucceeds_ShouldCloseCircuit() {
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        RedisConnection mockRedisConnection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(mockRedisConnection);
        when(mockRedisConnection.ping()).thenReturn("PONG");

        healthChecker.checkRedisHealth();

        verify(redisCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsOpen_AndPingFails_ShouldStayOpen() {
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Redis connection failed"));

        healthChecker.checkRedisHealth();

        verify(redisCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testRedisHealthCheck_WhenCircuitIsClosed_ShouldNotTest() {
        when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        healthChecker.checkRedisHealth();

        verify(redisConnectionFactory, never()).getConnection();
        verify(redisCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsOpen_AndMetricsSucceed_ShouldCloseCircuit() {
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        @SuppressWarnings("unchecked")
        ProducerFactory<String, Object> mockProducerFactory = mock(ProducerFactory.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn((ProducerFactory) mockProducerFactory);
        when(mockProducerFactory.getConfigurationProperties()).thenReturn(Collections.emptyMap());
        
        AdminClient mockAdminClient = mock(AdminClient.class);
        DescribeClusterResult mockClusterResult = mock(DescribeClusterResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<String> mockFuture = mock(KafkaFuture.class);
        
        when(mockClusterResult.clusterId()).thenReturn(mockFuture);
        when(mockAdminClient.describeCluster()).thenReturn(mockClusterResult);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create((Map<String, Object>) any())).thenReturn(mockAdminClient);
            healthChecker.checkKafkaHealth();
        }

        verify(kafkaCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsOpen_AndMetricsFail_ShouldStayOpen() {
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        @SuppressWarnings("unchecked")
        ProducerFactory<String, Object> mockProducerFactory = mock(ProducerFactory.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn((ProducerFactory) mockProducerFactory);
        when(mockProducerFactory.getConfigurationProperties()).thenReturn(Collections.emptyMap());
        
        AdminClient mockAdminClient = mock(AdminClient.class);
        DescribeClusterResult mockClusterResult = mock(DescribeClusterResult.class);
        
        when(mockAdminClient.describeCluster()).thenReturn(mockClusterResult);
        when(mockClusterResult.clusterId()).thenThrow(new RuntimeException("Kafka unavailable"));
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create((Map<String, Object>) any())).thenReturn(mockAdminClient);
            healthChecker.checkKafkaHealth();
        }

        verify(kafkaCircuitBreaker, never()).transitionToClosedState();
    }

    @Test
    void testKafkaHealthCheck_WhenCircuitIsClosed_ShouldNotTest() {
        when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        healthChecker.checkKafkaHealth();

        verify(kafkaTemplate, never()).getProducerFactory();
        verify(kafkaCircuitBreaker, never()).transitionToClosedState();
    }
}
