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
    private CircuitBreaker replicaCircuitBreaker;

    @Mock
    private CircuitBreaker redisCircuitBreaker;

    @Mock
    private CircuitBreaker kafkaCircuitBreaker;

    private CircuitBreakerHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(dbCircuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("replica")).thenReturn(replicaCircuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("redis")).thenReturn(redisCircuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("kafka")).thenReturn(kafkaCircuitBreaker);
        
        // Manually create the healthChecker with properly injected dependencies
        healthChecker = new CircuitBreakerHealthChecker(
                circuitBreakerRegistry,
                writerDataSource,
                readerDataSource,
                redisConnectionFactory,
                kafkaTemplate
        );
    }

    @Test
    void testProactiveDatabaseHealthCheck_WhenWriterHealthyReaderDown_ShouldNotOpenCircuitBreaker() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        Connection writerConn = mock(Connection.class);
        lenient().doNothing().when(writerConn).close();
        lenient().when(writerConn.isValid(2)).thenReturn(true);
        
        lenient().when(writerDataSource.getConnection()).thenReturn(writerConn);
        lenient().when(readerDataSource.getConnection()).thenThrow(new SQLException("Reader is down"));

        healthChecker.proactiveDbHealthCheck();

        // Should NOT open CB when only reader is down - fallback to writer handles it
        verify(dbCircuitBreaker, never()).onError(anyLong(), any(), any());
    }

    @Test
    void testProactiveDatabaseHealthCheck_WhenWriterDown_ShouldOpenCircuitBreaker() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        lenient().when(writerDataSource.getConnection()).thenThrow(new SQLException("Writer is down"));
        lenient().when(readerDataSource.getConnection()).thenThrow(new SQLException("Both down"));

        healthChecker.proactiveDbHealthCheck();

        // Should open CB when writer is down - this is critical
        verify(dbCircuitBreaker, times(1)).onError(anyLong(), any(), any());
    }

    @Test
    void testCheckDatabaseRecovery_WhenBothHealthy_ShouldCloseCircuitBreaker() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        Connection writerConn = mock(Connection.class);
        Connection readerConn = mock(Connection.class);
        lenient().doNothing().when(writerConn).close();
        lenient().doNothing().when(readerConn).close();
        
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
    void testProactiveReplicaHealthCheck_WhenReaderDown_ShouldOpenCircuitBreaker() throws SQLException {
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(readerDataSource.getConnection()).thenThrow(new SQLException("Reader is down"));

        healthChecker.proactiveReplicaHealthCheck();

        // Should open CB when reader is down
        verify(replicaCircuitBreaker, times(1)).onError(anyLong(), any(), any());
    }

    @Test
    void testProactiveReplicaHealthCheck_WhenReaderHealthy_ShouldNotOpenCircuitBreaker() throws SQLException {
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        Connection readerConn = mock(Connection.class);
        lenient().doNothing().when(readerConn).close();
        lenient().when(readerConn.isValid(2)).thenReturn(true);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);

        healthChecker.proactiveReplicaHealthCheck();

        // Should NOT open CB when reader is healthy
        verify(replicaCircuitBreaker, never()).onError(anyLong(), any(), any());
    }

    @Test
    void testCheckReplicaRecovery_WhenReaderHealthy_ShouldCloseCircuitBreaker() throws SQLException {
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        Connection readerConn = mock(Connection.class);
        lenient().doNothing().when(readerConn).close();
        lenient().when(readerConn.isValid(5)).thenReturn(true);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);

        healthChecker.checkReplicaRecovery();

        verify(replicaCircuitBreaker).transitionToClosedState();
    }

    @Test
    void testProactiveHealthChecks_ShouldExecuteWithoutErrors() throws SQLException {
        lenient().when(dbCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(redisCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(kafkaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        Connection writerConn = mock(Connection.class);
        Connection readerConn = mock(Connection.class);
        RedisConnection redisConn = mock(RedisConnection.class);
        lenient().doNothing().when(writerConn).close();
        lenient().doNothing().when(readerConn).close();
        
        lenient().when(writerDataSource.getConnection()).thenReturn(writerConn);
        lenient().when(readerDataSource.getConnection()).thenReturn(readerConn);
        lenient().when(writerConn.isValid(2)).thenReturn(true);
        lenient().when(readerConn.isValid(2)).thenReturn(true);
        lenient().when(redisConnectionFactory.getConnection()).thenReturn(redisConn);
        lenient().when(redisConn.ping()).thenReturn("PONG");

        // Should execute without throwing exceptions
        healthChecker.proactiveDbHealthCheck();
        healthChecker.proactiveReplicaHealthCheck();
        healthChecker.proactiveRedisHealthCheck();
        healthChecker.proactiveKafkaHealthCheck();
    }
}
