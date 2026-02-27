package com.economato.inventory.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@Order(0)
@Profile("!test")
public class DataSourceAspect {

    @Around("@annotation(transactional)")
    public Object proceed(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        DataSourceType type = transactional.readOnly() ? DataSourceType.READER : DataSourceType.WRITER;
        return ScopedValue.where(DbContextHolder.CONTEXT, type)
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
}
