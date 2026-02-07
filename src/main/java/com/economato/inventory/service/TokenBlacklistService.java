package com.economato.inventory.service;

import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio para gestionar la lista negra de tokens JWT invalidados.
 * Utiliza una estructura en memoria para almacenar tokens que han sido revocados.
 */
@Service
public class TokenBlacklistService {

    // Mapa que almacena el token como clave y la fecha de expiración como valor
    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Añade un token a la lista negra con su fecha de expiración.
     * 
     * @param token El token JWT a invalidar
     * @param expirationDate La fecha de expiración del token
     */
    public void blacklistToken(String token, Date expirationDate) {
        if (token != null && !token.isEmpty()) {
            blacklistedTokens.put(token, expirationDate);
        }
    }

    /**
     * Verifica si un token está en la lista negra.
     * 
     * @param token El token JWT a verificar
     * @return true si el token está en la lista negra, false en caso contrario
     */
    public boolean isBlacklisted(String token) {
        // Limpiar tokens expirados antes de verificar
        cleanExpiredTokens();
        return token != null && blacklistedTokens.containsKey(token);
    }

    /**
     * Limpia los tokens que ya han expirado de la lista negra.
     * Este método es llamado automáticamente al verificar tokens.
     */
    private void cleanExpiredTokens() {
        Date now = new Date();
        blacklistedTokens.entrySet().removeIf(entry -> 
            entry.getValue() != null && entry.getValue().before(now)
        );
    }

    /**
     * Obtiene el número de tokens actualmente en la lista negra.
     * 
     * @return El número de tokens en la lista negra
     */
    public int getBlacklistSize() {
        cleanExpiredTokens();
        return blacklistedTokens.size();
    }

    /**
     * Limpia todos los tokens de la lista negra.
     * Útil para pruebas o mantenimiento.
     */
    public void clearBlacklist() {
        blacklistedTokens.clear();
    }
}
