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
        try {
            if (transactional.readOnly()) {
                DbContextHolder.set(DataSourceType.READER);
            } else {
                DbContextHolder.set(DataSourceType.WRITER);
            }
            return pjp.proceed();
        } finally {
            DbContextHolder.clear();
        }
    }
}
