package com.economato.inventory.application.usecase;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

/**
 * Servicio que adapta entidades de usuario del dominio a UserDetails de Spring Security y añade
 * un cache en memoria para reducir llamadas a la capa de persistencia.
 *
 * Necesidad: centralizar la lógica de carga de usuarios para autenticación (búsqueda por nombre o
 * usuario, validación de usuarios ocultos y mensajes internacionalizados) y mejorar el rendimiento
 * evitando hits repetidos a la base de datos mediante un cache con TTL.
 *
 * Comportamiento principal:
 *  - loadUserByUsername(String): devuelve UserDetails (usa cache si la entrada es válida; si no, consulta
 *    UserRepository y lanza UsernameNotFoundException con mensajes i18n si procede).
 *  - evictUser(String): elimina la entrada del cache para un usuario concreto.
 *  - clearCache(): limpia todo el cache.
 *
 * Detalles de implementación:
 *  - Cache en ConcurrentHashMap con TTL de 15 minutos por entrada.
 *  - Clase marcada como @Transactional(readOnly = true).
 *  - Thread-safe gracias al uso de colecciones concurrentes.
 */
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutos

    private final I18nService i18nService;
    private final UserRepository userRepository;
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public CustomUserDetailsService(I18nService i18nService, UserRepository userRepository) {
        this.i18nService = i18nService;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        CachedEntry cached = cache.get(username);
        if (cached != null && !cached.isExpired()) {
            return cached.toUserDetails();
        }

        User user = userRepository.findByName(username)
                .or(() -> userRepository.findByUser(username))
                .orElseThrow(() -> new UsernameNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_AUTH_USER_NOT_FOUND, new Object[] { username })));

        // Validar que el usuario no esté oculto
        if (user.isHidden()) {
            throw new UsernameNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_AUTH_USER_HIDDEN, new Object[] { username }));
        }

        CachedEntry entry = new CachedEntry(user.getId(), user.getName(), user.getPassword(), "ROLE_" + user.getRole());
        cache.put(username, entry);
        return entry.toUserDetails();
    }

    public void evictUser(String username) {
        cache.remove(username);
    }

    public void clearCache() {
        cache.clear();
    }
    private static class CachedEntry {
        private final long timestamp;
        private final Integer userId;
        private final String username;
        private final String password;
        private final String authority;

        CachedEntry(Integer userId, String username, String password, String authority) {
            this.timestamp = System.currentTimeMillis();
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.authority = authority;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }

        UserDetails toUserDetails() {
            List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authority));
            return new FastUserDetails(userId, username, password, authorities);
        }
    }

    /**
     * Reusable UserDetails implementation.
     */
    public static class FastUserDetails extends org.springframework.security.core.userdetails.User {
        private final Integer userId;

        public FastUserDetails(Integer userId, String username, String password,
                Collection<? extends GrantedAuthority> authorities) {
            super(username, password, authorities);
            this.userId = userId;
        }

        public Integer getUserId() {
            return userId;
        }
    }
}