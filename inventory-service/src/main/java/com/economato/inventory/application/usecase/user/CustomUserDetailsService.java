package com.economato.inventory.application.usecase.user;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

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

    private final I18nService i18nService;
    private final UserRepository userRepository;
    private final Cache<String, UserDetails> userDetailsLocalCache;

    public CustomUserDetailsService(I18nService i18nService,
            UserRepository userRepository,
            @Qualifier("userDetailsLocalCache")
            Cache<String, UserDetails> userDetailsLocalCache) {
        this.i18nService = i18nService;
        this.userRepository = userRepository;
        this.userDetailsLocalCache = userDetailsLocalCache;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails cached = userDetailsLocalCache.get(username, this::loadFromDB);
        return copyUserDetails(cached);
    }

    private UserDetails copyUserDetails(UserDetails source) {
        if (source == null) {
            throw new UsernameNotFoundException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_NOT_FOUND));
        }

        String username = Objects.requireNonNullElse(source.getUsername(), "");
        String password = Objects.requireNonNullElse(source.getPassword(), "");

        if (source instanceof FastUserDetails fastUserDetails) {
            return new FastUserDetails(
                    fastUserDetails.getUserId(),
                    username,
                    password,
                    fastUserDetails.getAuthorities().stream().collect(Collectors.toList()));
        }

        return new org.springframework.security.core.userdetails.User(
                username,
                password,
                source.getAuthorities().stream().collect(Collectors.toList()));
    }

    private UserDetails loadFromDB(String username) {
        User user = userRepository.findByName(username)
                .or(() -> userRepository.findByUser(username))
                .orElseThrow(() -> new UsernameNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_AUTH_USER_NOT_FOUND, new Object[] { username })));

        // Validar que el usuario no esté oculto
        if (user.isHidden()) {
            throw new UsernameNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_AUTH_USER_HIDDEN, new Object[] { username }));
        }

        List<GrantedAuthority> authorities = Collections
                .singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        return new FastUserDetails(user.getId(), user.getName(), user.getPassword(), authorities);
    }

    public void evictUser(String username) {
        userDetailsLocalCache.invalidate(username);
    }

    public void clearCache() {
        userDetailsLocalCache.invalidateAll();
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