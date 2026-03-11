package com.economato.inventory.infrastructure.config.database;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.config.security.SpringSecurityAuditorAware;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {

    private final SpringSecurityAuditorAware springSecurityAuditorAware;

    public JpaConfig(SpringSecurityAuditorAware springSecurityAuditorAware) {
        this.springSecurityAuditorAware = springSecurityAuditorAware;
    }

    @Bean
    public AuditorAware<User> auditorProvider() {
        return springSecurityAuditorAware;
    }
}
