package com.economato.inventory.aop;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component("customCircuitBreakerAspect")
@RequiredArgsConstructor
public class CustomCircuitBreakerAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Intercept all public methods in classes annotated with @Repository
     * or any class under com.economato.inventory.repository
     */
    @Pointcut("within(com.economato.inventory.repository..*) || @within(org.springframework.stereotype.Repository)")
    public void databaseOperations() {
    }

    /**
     * Intercept all methods annotated with Spring Cache annotations,
     * effectively intercepting Redis operations.
     */
    @Pointcut("@annotation(org.springframework.cache.annotation.Cacheable) || " +
            "@annotation(org.springframework.cache.annotation.CachePut) || " +
            "@annotation(org.springframework.cache.annotation.CacheEvict)")
    public void cacheOperations() {
    }

    @Around("databaseOperations()")
    public Object applyDbCircuitBreaker(ProceedingJoinPoint joinPoint) throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        return circuitBreaker.executeCheckedSupplier(joinPoint::proceed);
    }

    @Around("cacheOperations()")
    public Object applyRedisCircuitBreaker(ProceedingJoinPoint joinPoint) throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        try {
            return circuitBreaker.executeCheckedSupplier(joinPoint::proceed);
        } catch (Throwable ex) {
            log.warn("Redis operation failed or circuit open. Bypassing cache for {}, reason: {}",
                    joinPoint.getSignature(), ex.getMessage());

            if (ex instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException ||
                    ex instanceof org.springframework.data.redis.RedisConnectionFailureException) {
                return joinPoint.proceed();
            }
            throw ex;
        }
    }
}
