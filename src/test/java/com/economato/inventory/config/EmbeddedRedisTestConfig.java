package com.economato.inventory.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

/**
 * Starts an embedded Redis server for resilience E2E tests.
 * Uses port 6370 to avoid conflicts with a local Redis instance.
 */
@TestConfiguration
@Profile("resilience-test")
public class EmbeddedRedisTestConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        redisServer = new RedisServer(6370);
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }
}
