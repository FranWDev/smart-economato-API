package com.economato.user.application.port.out;

import java.util.Date;

public interface TokenBlacklistPort {
    void blacklistToken(String token, Date expirationDate);
    boolean isBlacklisted(String token);
}
