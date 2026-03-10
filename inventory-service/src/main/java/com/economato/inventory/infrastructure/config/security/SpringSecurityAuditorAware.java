package com.economato.inventory.infrastructure.config.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

import java.util.Optional;

@Component
public class SpringSecurityAuditorAware implements AuditorAware<User> {

    private final UserRepository userRepository;

    public SpringSecurityAuditorAware(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String username = auth.getName();
            return userRepository.findByName(username);
        }

        return Optional.empty();
    }
}
