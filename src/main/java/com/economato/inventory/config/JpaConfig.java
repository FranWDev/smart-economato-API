package com.economato.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.economato.inventory.model.User;

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
