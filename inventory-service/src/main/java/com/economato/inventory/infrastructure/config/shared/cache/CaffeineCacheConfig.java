package com.economato.inventory.infrastructure.config.shared.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, Locale> tokenLocaleCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(10000)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<Object, Object> masterDataCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)
                .maximumSize(500)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<Object, Object> hotEntityCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .maximumSize(2000)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, org.springframework.security.core.userdetails.UserDetails> userDetailsLocalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build();
    }

    @Bean(name = "l1CaffeineCacheManager")
    public CacheManager l1CaffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(5000)
                .recordStats());
        return manager;
    }
}
