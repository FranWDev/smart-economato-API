package com.economato.inventory.service;

import java.util.Date;

public interface TokenBlacklistService {
    void blacklistToken(String token, Date expirationDate);

    boolean isBlacklisted(String token);

    void clearBlacklist();

    int getBlacklistSize();

    void cleanExpiredTokens();
}
