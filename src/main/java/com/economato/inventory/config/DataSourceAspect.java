package com.economato.inventory.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Aspect
@Component
@Order(0)
@Profile("!test")
@RequiredArgsConstructor
public class DataSourceAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @SuppressWarnings("preview")
    @Around("@annotation(transactional)")
    public Object proceed(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        DataSourceType type = transactional.readOnly() ? DataSourceType.READER : DataSourceType.WRITER;
        
        // Check if DB or replica circuit breaker is open to use writer as fallback
        CircuitBreaker dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
        boolean useWriterFallback = type == DataSourceType.READER
            && (dbCircuitBreaker.getState() == CircuitBreaker.State.OPEN
                || replicaCircuitBreaker.getState() == CircuitBreaker.State.OPEN);
        
        if (useWriterFallback) {
            log.debug("DB/REPLICA circuit breaker is OPEN, using WRITER datasource as fallback for read operation");
            type = DataSourceType.WRITER;
        }
        
        DataSourceType finalType = type;
        
        return ScopedValue.where(DbContextHolder.CONTEXT, finalType)
                .call(() -> {
                    try {
                        return pjp.proceed();
                    } catch (Throwable t) {
                        // If reading from READER fails with connection error, retry with WRITER as fallback
                        if (finalType == DataSourceType.READER && isConnectionException(t) && transactional.readOnly()) {
                            log.warn("Read operation failed on READER datasource, retrying with WRITER as fallback: {}", 
                                    t.getMessage());

                            // Register READER failure in replica circuit breaker to notify frontend
                            // while still allowing graceful fallback to WRITER.
                                replicaCircuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
                                    resolveRootCause(t));

                            return retryWithWriter(pjp);
                        }
                        
                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else if (t instanceof Error) {
                            throw (Error) t;
                        }
                        throw new RuntimeException(t);
                    }
                });
    }
    
    @SuppressWarnings("preview")
    private Object retryWithWriter(ProceedingJoinPoint pjp) throws Throwable {
        return ScopedValue.where(DbContextHolder.CONTEXT, DataSourceType.WRITER)
                .call(() -> {
                    try {
                        return pjp.proceed();
                    } catch (Throwable t) {
                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else if (t instanceof Error) {
                            throw (Error) t;
                        }
                        throw new RuntimeException(t);
                    }
                });
    }
    
    private boolean isConnectionException(Throwable t) {
        if (t == null) return false;
        
        // Check the exception and its causes for connection-related issues
        Throwable current = t;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            
            if (current instanceof DataAccessResourceFailureException ||
                className.contains("JDBCConnectionException") ||
                className.contains("SQLTransientConnectionException") ||
                className.contains("UnknownHostException") ||
                className.contains("ConnectException") ||
                message.contains("connection") ||
                message.contains("refused") ||
                message.contains("timeout")) {
                return true;
            }
            
            current = current.getCause();
        }
        
        return false;
    }

    private Throwable resolveRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : throwable;
    }
}
