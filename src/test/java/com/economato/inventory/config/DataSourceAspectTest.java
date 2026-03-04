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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceAspectTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

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
    }

    @Test
    void testReadOperationUsesReaderWhenCircuitBreakerClosed() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(proceedingJoinPoint.proceed()).thenReturn("success");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationUsesWriterWhenCircuitBreakerOpen() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(proceedingJoinPoint.proceed()).thenReturn("success from writer");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationFallbackToWriterOnConnectionError() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        // First call: fail with connection exception
        // Second call: succeed with writer
        when(proceedingJoinPoint.proceed())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"))
                .thenReturn("success from writer fallback");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer fallback", result);
        verify(proceedingJoinPoint, times(2)).proceed();
        // Verify error was registered in circuit breaker
        verify(circuitBreaker, times(1)).onError(eq(0L), eq(java.util.concurrent.TimeUnit.MILLISECONDS), 
                any(DataAccessResourceFailureException.class));
    }

    @Test
    void testReadOperationFallbackToWriterOnUnknownHostException() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        SQLException sqlException = new SQLException(new UnknownHostException("postgres-replica"));
        JDBCConnectionException jdbcException = new JDBCConnectionException("Cannot resolve host", sqlException);
        
        when(proceedingJoinPoint.proceed())
                .thenThrow(new RuntimeException(jdbcException))
                .thenReturn("success from writer fallback");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional);

        assertEquals("success from writer fallback", result);
        verify(proceedingJoinPoint, times(2)).proceed();
        // Verify error was registered in circuit breaker
        verify(circuitBreaker, times(1)).onError(eq(0L), eq(java.util.concurrent.TimeUnit.MILLISECONDS), 
                any(RuntimeException.class));
    }

    @Test
    void testWriteOperationAlwaysUsesWriter() throws Throwable {
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(proceedingJoinPoint.proceed()).thenReturn("write success");

        Object result = dataSourceAspect.proceed(proceedingJoinPoint, writeTransactional);

        assertEquals("write success", result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testWriteOperationDoesNotFallbackOnError() throws Throwable {
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        lenient().when(proceedingJoinPoint.proceed())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        assertThrows(DataAccessResourceFailureException.class, 
                () -> dataSourceAspect.proceed(proceedingJoinPoint, writeTransactional));

        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    void testReadOperationDoesNotFallbackOnNonConnectionError() throws Throwable {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(proceedingJoinPoint.proceed())
                .thenThrow(new IllegalArgumentException("Invalid parameter"));

        assertThrows(IllegalArgumentException.class, 
                () -> dataSourceAspect.proceed(proceedingJoinPoint, readOnlyTransactional));

        verify(proceedingJoinPoint, times(1)).proceed();
    }
}
