package com.economato.inventory.infrastructure.aspect.shared;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component("customCircuitBreakerAspect")
@RequiredArgsConstructor
public class CustomCircuitBreakerAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Intercepta todas las operaciones de base de datos, incluyendo repositorios y cualquier clase anotada con @Repository.
     */
    @Pointcut("within(com.economato.inventory.repository..*) || @within(org.springframework.stereotype.Repository)")
    public void databaseOperations() {
    }

    @Around("databaseOperations()")
    public Object applyDbCircuitBreaker(ProceedingJoinPoint joinPoint) throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
        return circuitBreaker.executeCheckedSupplier(joinPoint::proceed);
    }
}
