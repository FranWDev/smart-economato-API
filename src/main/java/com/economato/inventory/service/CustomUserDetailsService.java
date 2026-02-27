package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;

import java.util.Collections;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;

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
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Validar que el usuario no esté oculto
        if (user.isHidden()) {
            throw new UsernameNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_AUTH_USER_HIDDEN) + ": " + username);
        }

        CachedEntry entry = new CachedEntry(user.getName(), user.getPassword(), "ROLE_" + user.getRole());
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
        private final String username;
        private final String password;
        private final String authority;

        CachedEntry(String username, String password, String authority) {
            this.timestamp = System.currentTimeMillis();
            this.username = username;
            this.password = password;
            this.authority = authority;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }

        UserDetails toUserDetails() {
            List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authority));
            return new FastUserDetails(username, password, authorities);
        }
    }

    /**
     * Reusable UserDetails implementation.
     */
    private static class FastUserDetails extends org.springframework.security.core.userdetails.User {
        public FastUserDetails(String username, String password,
                Collection<? extends GrantedAuthority> authorities) {
            super(username, password, authorities);
        }
    }
}