package com.economato.inventory.infrastructure.config.shared.security;

import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                return userRepository != null ? userRepository.findByName(username).orElse(null) : null;
            }
        } catch (Exception e) {
            log.warn("Error retrieving current user from SecurityContext", e);
        }
        return null;
    }

    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Error retrieving current username from SecurityContext", e);
        }
        return null;
    }
}
