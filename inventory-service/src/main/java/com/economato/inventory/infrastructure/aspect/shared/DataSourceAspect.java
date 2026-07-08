package com.economato.inventory.infrastructure.aspect.shared;

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

import java.util.concurrent.TimeUnit;

import com.economato.inventory.infrastructure.config.shared.database.DataSourceType;
import com.economato.inventory.infrastructure.config.shared.database.DbContextHolder;


/**
 * Aspecto para enrutar dinámicamente entre datasources de lectura y escritura (CQRS).
 * Aplica un fallback automático a la datasource de escritura si la de lectura falla por problemas de conexión.
 * También integra circuit breakers para monitorear la salud de las conexiones a las bases de datos.
 */
@Slf4j
@Aspect
@Component
@Order(0)
@Profile("!test")
@RequiredArgsConstructor
public class DataSourceAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Around("@annotation(transactional)")
    public Object proceed(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        DataSourceType type = transactional.readOnly() ? DataSourceType.READER : DataSourceType.WRITER;
        
        // Revisar el estado de los circuit breakers para decidir si se debe usar el datasource de escritura como fallback para lecturas
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
        /*
         * ScopedValue es una característica de Java 21 que permite asociar
         un valor a un contexto de ejecución sin necesidad de pasar 
         explícitamente ese valor a través de los métodos. 
         */
        return ScopedValue.where(DbContextHolder.CONTEXT, finalType)
                .call(() -> {
                    try {
                        return pjp.proceed();
                    } catch (Throwable t) {
                        // Si es una operación de lectura y falla por un problema de conexión, intentar con el datasource de escritura como fallback.
                        if (finalType == DataSourceType.READER && isConnectionException(t) && transactional.readOnly()) {
                            log.warn("Read operation failed on READER datasource, retrying with WRITER as fallback: {}", 
                                    t.getMessage());

                            // Registra el error en los circuit breakers correspondientes
                                replicaCircuitBreaker.onError(0, TimeUnit.MILLISECONDS,
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
        
        // Revisar toda la cadena de causas para detectar excepciones relacionadas con problemas de conexión a la base de datos
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
