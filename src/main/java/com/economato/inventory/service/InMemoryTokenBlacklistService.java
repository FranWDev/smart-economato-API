package com.economato.inventory.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("test")
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    @Override
    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty()) {
            blacklistedTokens.put(token, expirationDate);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        return token != null && blacklistedTokens.containsKey(token);
    }

    @Override
    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredTokens() {
        Date now = new Date();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().before(now));
    }

    @Override
    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }

    @Override
    public void clearBlacklist() {
        blacklistedTokens.clear();
    }

    /**
     * Interface doesn't strictly need cleanExpiredTokens exposed,
     * but we keep the scheduled task for internal cleanup in tests.
     */
}
