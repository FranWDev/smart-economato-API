package com.economato.inventory.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private static final long CACHE_TTL_MS = 15 * 60 * 1000;

    private final UserRepository userRepository;
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        CachedEntry cached = cache.get(username);
        if (cached != null && !cached.isExpired()) {
            return cached.toUserDetails();
        }

        User user = userRepository.findByName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Validar que el usuario no esté oculto
        if (user.isHidden()) {
            throw new UsernameNotFoundException("User is hidden and cannot login: " + username);
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
        final String username;
        final String password;
        final String authority;
        final long timestamp;

        CachedEntry(String username, String password, String authority) {
            this.username = username;
            this.password = password;
            this.authority = authority;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }

        UserDetails toUserDetails() {
            return org.springframework.security.core.userdetails.User
                    .withUsername(username)
                    .password(password)
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority(authority)))
                    .build();
        }
    }
}