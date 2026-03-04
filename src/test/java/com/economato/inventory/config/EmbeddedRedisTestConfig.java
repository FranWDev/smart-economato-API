package com.economato.inventory.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

/**
 * Starts an embedded Redis server for resilience E2E tests.
 * Uses port 6370 to avoid conflicts with a local Redis instance.
 * On Windows, may fail silently and allow tests to continue.
 */
@Slf4j
@TestConfiguration
@Profile("resilience-test")
public class EmbeddedRedisTestConfig {

    private RedisServer redisServer;
    private boolean redisStarted = false;

    @PostConstruct
    public void startRedis() {
        try {
            // Try to start embedded Redis on port 6370
            redisServer = new RedisServer(6370);
            redisServer.start();
            redisStarted = true;
            log.info("Embedded Redis started successfully on port 6370");
        } catch (Exception e) {
            // Log error but don't fail - allows tests to continue without Redis
            log.warn("Failed to start embedded Redis on port 6370: {}. Tests will continue without Redis.", 
                    e.getMessage());
            redisStarted = false;
            // Try to find an existing Redis instance or continue without it
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisStarted && redisServer != null && redisServer.isActive()) {
            try {
                redisServer.stop();
                log.info("Embedded Redis stopped");
            } catch (Exception e) {
                log.warn("Error stopping embedded Redis: {}", e.getMessage());
            }
        }
    }
}
