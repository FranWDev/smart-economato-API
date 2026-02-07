package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class TokenBlacklistServiceTest {

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService();
    }

    @Test
    void testBlacklistToken() {
        String token = "test.jwt.token";
        Date expiration = new Date(System.currentTimeMillis() + 3600000); // 1 hora en el futuro

        tokenBlacklistService.blacklistToken(token, expiration);

        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testIsBlacklistedReturnsFalseForNonBlacklistedToken() {
        String token = "non.blacklisted.token";

        assertFalse(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testBlacklistTokenWithNullToken() {
        tokenBlacklistService.blacklistToken(null, new Date());

        assertFalse(tokenBlacklistService.isBlacklisted(null));
    }

    @Test
    void testBlacklistTokenWithEmptyToken() {
        tokenBlacklistService.blacklistToken("", new Date());

        assertFalse(tokenBlacklistService.isBlacklisted(""));
    }

    @Test
    void testCleanExpiredTokens() throws InterruptedException {
        String token = "expired.token";
        // Crear una fecha de expiración en el pasado
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, -1);
        Date expiration = calendar.getTime();

        tokenBlacklistService.blacklistToken(token, expiration);

        // Esperar un poco para asegurar que el token expire
        Thread.sleep(100);

        // Al verificar, el servicio limpia automáticamente los tokens expirados
        assertFalse(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testGetBlacklistSize() {
        assertEquals(0, tokenBlacklistService.getBlacklistSize());

        Date expiration = new Date(System.currentTimeMillis() + 3600000);
        tokenBlacklistService.blacklistToken("token1", expiration);
        tokenBlacklistService.blacklistToken("token2", expiration);

        assertEquals(2, tokenBlacklistService.getBlacklistSize());
    }

    @Test
    void testClearBlacklist() {
        Date expiration = new Date(System.currentTimeMillis() + 3600000);
        tokenBlacklistService.blacklistToken("token1", expiration);
        tokenBlacklistService.blacklistToken("token2", expiration);

        assertEquals(2, tokenBlacklistService.getBlacklistSize());

        tokenBlacklistService.clearBlacklist();

        assertEquals(0, tokenBlacklistService.getBlacklistSize());
    }

    @Test
    void testMultipleTokensBlacklisting() {
        Date expiration = new Date(System.currentTimeMillis() + 3600000);

        for (int i = 0; i < 10; i++) {
            tokenBlacklistService.blacklistToken("token" + i, expiration);
        }

        assertEquals(10, tokenBlacklistService.getBlacklistSize());

        for (int i = 0; i < 10; i++) {
            assertTrue(tokenBlacklistService.isBlacklisted("token" + i));
        }
    }
}
