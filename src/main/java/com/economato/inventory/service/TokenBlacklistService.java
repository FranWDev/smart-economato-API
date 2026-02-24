package com.economato.inventory.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty()) {
            blacklistedTokens.put(token, expirationDate);
        }
    }

    public boolean isBlacklisted(String token) {
        return token != null && blacklistedTokens.containsKey(token);
    }

    @Scheduled(fixedRate = 300_000)
    void cleanExpiredTokens() {
        Date now = new Date();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().before(now));
    }

    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }

    public void clearBlacklist() {
        blacklistedTokens.clear();
    }
}
