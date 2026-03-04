package com.economato.inventory.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerHealthCheckerTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private DataSource writerDataSource;

    @Mock
    private DataSource readerDataSource;

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

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
    }

    @Test
    void testProactiveDatabaseHealthCheck_WhenBothDatabasesHealthy_ShouldNotOpenCircuitBreaker() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        Connection writerConn = mock(Connection.class);
        Connection readerConn = mock(Connection.class);
        
        lenient().when(writerDataSource.getConnection()).thenReturn(writerConn);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);
        lenient().when(writerConn.isValid(2)).thenReturn(true);
        lenient().when(readerConn.isValid(2)).thenReturn(true);

        healthChecker.proactiveDbHealthCheck();

        verify(dbCircuitBreaker, never()).onError(anyLong(), any(), any());
    }

    @Test
    void testCheckDatabaseRecovery_WhenBothHealthy_ShouldCloseCircuitBreaker() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        Connection writerConn = mock(Connection.class);
        Connection readerConn = mock(Connection.class);
        
        lenient().when(writerDataSource.getConnection()).thenReturn(writerConn);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);
        lenient().when(writerConn.isValid(5)).thenReturn(true);
        lenient().when(readerConn.isValid(5)).thenReturn(true);

        healthChecker.checkDatabaseRecovery();

        verify(dbCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testCheckRedisRecovery_WhenHealthy_ShouldCloseCircuitBreaker() {
        lenient().when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        RedisConnection redisConn = mock(RedisConnection.class);
        lenient().when(redisConnectionFactory.getConnection()).thenReturn(redisConn);
        lenient().when(redisConn.ping()).thenReturn("PONG");

        healthChecker.checkRedisRecovery();

        verify(redisCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testProactiveHealthChecks_ShouldExecuteWithoutErrors() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        Connection writerConn = mock(Connection.class);
        Connection readerConn = mock(Connection.class);
        RedisConnection redisConn = mock(RedisConnection.class);
        
        lenient().when(writerDataSource.getConnection()).thenReturn(writerConn);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);
        lenient().when(writerConn.isValid(2)).thenReturn(true);
        lenient().when(readerConn.isValid(2)).thenReturn(true);
        lenient().when(redisConnectionFactory.getConnection()).thenReturn(redisConn);
        lenient().when(redisConn.ping()).thenReturn("PONG");

        // Should execute without throwing exceptions
        healthChecker.proactiveDbHealthCheck();
        healthChecker.proactiveRedisHealthCheck();
        healthChecker.proactiveKafkaHealthCheck();
    }
}
