package com.economato.inventory.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
@Profile("!test")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "token_blacklist:";

    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty() && expirationDate != null) {
            long ttlMillis = expirationDate.getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + token,
                        "revoked",
                        Duration.ofMillis(ttlMillis));
            }
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    @Override
    public void clearBlacklist() {
        var keys = redisTemplate.keys(BLACKLIST_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public int getBlacklistSize() {
        var keys = redisTemplate.keys(BLACKLIST_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    @Override
    public void cleanExpiredTokens() {
        // No-op for Redis as it uses TTL
    }
}
