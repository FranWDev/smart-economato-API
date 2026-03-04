package com.economato.inventory.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.economato.inventory.model.RevokedToken;
import com.economato.inventory.repository.RevokedTokenRepository;

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist for testing.
 * Also backs up to database for consistency with production behavior.
 */
@Slf4j
@Service
@Profile("test")
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();
    private final Cache<String, Locale> tokenLocaleCache;
    private final RevokedTokenRepository revokedTokenRepository;

    public InMemoryTokenBlacklistService(Cache<String, Locale> tokenLocaleCache,
            RevokedTokenRepository revokedTokenRepository) {
        this.tokenLocaleCache = tokenLocaleCache;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty()) {
            blacklistedTokens.put(token, expirationDate);
            try {
                revokedTokenRepository.save(new RevokedToken(token, expirationDate));
            } catch (Exception e) {
                log.warn("Failed to save revoked token to database: {}", e.getMessage());
            }
            tokenLocaleCache.invalidate(token);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        
        // Check in-memory cache first
        if (blacklistedTokens.containsKey(token)) {
            return true;
        }
        
        // Check database as fallback
        try {
            return revokedTokenRepository.existsByToken(token);
        } catch (Exception e) {
            log.debug("Failed to check database for revoked token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredTokens() {
        Date now = new Date();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().before(now));
        
        try {
            revokedTokenRepository.deleteExpiredTokens(now);
        } catch (Exception e) {
            log.warn("Failed to clean expired tokens from database: {}", e.getMessage());
        }
    }

    @Override
    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }

    @Override
    public void clearBlacklist() {
        blacklistedTokens.clear();
        try {
            revokedTokenRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Failed to clear database blacklist: {}", e.getMessage());
        }
    }
}

