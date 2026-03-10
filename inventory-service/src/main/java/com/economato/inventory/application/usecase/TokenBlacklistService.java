package com.economato.inventory.application.usecase;

import java.util.Date;

public interface TokenBlacklistService {
    void blacklistToken(String token, Date expirationDate);

    boolean isBlacklisted(String token);

    void clearBlacklist();

    int getBlacklistSize();

    void cleanExpiredTokens();
}
