package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceAspectTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private CircuitBreaker replicaCircuitBreaker;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @InjectMocks
    private DataSourceAspect dataSourceAspect;

    private Transactional readOnlyTransactional;
    private Transactional writeTransactional;

    @BeforeEach
    void setUp() {
        readOnlyTransactional = mock(Transactional.class);
        lenient().when(readOnlyTransactional.readOnly()).thenReturn(true);

        writeTransactional = mock(Transactional.class);
        lenient().when(writeTransactional.readOnly()).thenReturn(false);

        lenient().when(circuitBreakerRegistry.circuitBreaker("db")).thenReturn(circuitBreaker);
        lenient().when(circuitBreakerRegistry.circuitBreaker("replica")).thenReturn(replicaCircuitBreaker);
    }

    @Test
    void testReadOperationUsesReaderWhenCircuitBreakerClosed() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(proceedingJoinPoint.proceed()).thenReturn("success");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationUsesWriterWhenCircuitBreakerOpen() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(proceedingJoinPoint.proceed()).thenReturn("success from writer");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationFallbackToWriterOnConnectionError() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        // First call: fail with connection exception
        // Second call: succeed with writer
        when(proceedingJoinPoint.proceed())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"))
                .thenReturn("success from writer fallback");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer fallback", result);
        verify(proceedingJoinPoint, times(2)).proceed();
        verify(replicaCircuitBreaker, times(1)).onError(anyLong(), any(), any());
    }

    @Test
    void testReadOperationFallbackToWriterOnUnknownHostException() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        SQLException sqlException = new SQLException(new UnknownHostException("postgres-replica"));
        JDBCConnectionException jdbcException = new JDBCConnectionException("Cannot resolve host", sqlException);
        
        when(proceedingJoinPoint.proceed())
                .thenThrow(new RuntimeException(jdbcException))
                .thenReturn("success from writer fallback");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer fallback", result);
        verify(proceedingJoinPoint, times(2)).proceed();
        verify(replicaCircuitBreaker, times(1)).onError(anyLong(), any(), any());
    }

    @Test
    void testWriteOperationAlwaysUsesWriter() throws Throwable {
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(proceedingJoinPoint.proceed()).thenReturn("write success");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, writeTransactional);

        assertEquals("write success", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testWriteOperationDoesNotFallbackOnError() throws Throwable {
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(proceedingJoinPoint.proceed())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        assertThrows(DataAccessResourceFailureException.class, 
                () -> dataSourceAspect.proceed(proceedingJoinPoint, writeTransactional));

        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationDoesNotFallbackOnNonConnectionError() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(proceedingJoinPoint.proceed())
                .thenThrow(new IllegalArgumentException("Invalid parameter"));

        assertThrows(IllegalArgumentException.class, 
                () -> dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional));

        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationUsesWriterWhenReplicaCircuitBreakerOpen() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(replicaCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(proceedingJoinPoint.proceed()).thenReturn("success from writer because replica is open");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer because replica is open", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }
}
