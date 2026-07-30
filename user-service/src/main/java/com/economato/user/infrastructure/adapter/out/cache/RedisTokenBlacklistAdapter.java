package com.economato.user.infrastructure.adapter.out.cache;

import com.economato.user.application.port.out.TokenBlacklistPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String BLACKLIST_KEY_PREFIX = "token_blacklist:";
    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistToken(String token, Date expirationDate) {
        if (token == null || expirationDate == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long expirationTime = expirationDate.getTime();
        long timeToLive = expirationTime - now;

        if (timeToLive > 0) {
            String key = BLACKLIST_KEY_PREFIX + token;
            redisTemplate.opsForValue().set(key, "true", timeToLive, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(key));
    }
}
