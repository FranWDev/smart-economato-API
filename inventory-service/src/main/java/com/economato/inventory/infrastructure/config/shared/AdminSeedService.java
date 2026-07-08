package com.economato.inventory.infrastructure.config.shared;

import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@Order(1)
@RequiredArgsConstructor
public class AdminSeedService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin.name:Admin}")
    private String adminName;

    @Value("${app.seed.admin.user:admin}")
    private String adminUser;

    @Value("${app.seed.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        long adminCount = userRepository.countByRole(Role.ADMIN);

        if (adminCount > 0) {
            log.info("Ya existen {} administradores, omitiendo seed.", adminCount);
            return;
        }

        log.warn("No se encontraron administradores. Creando usuario admin inicial...");

        User admin = new User();
        admin.setName(adminName);
        admin.setUser(adminUser);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setFirstLogin(true);
        admin.setHidden(false);

        userRepository.save(admin);
        log.warn("Admin inicial creado (usuario: '{}'). Se requiere cambio de contraseña en el primer login.", adminUser);
    }
}
