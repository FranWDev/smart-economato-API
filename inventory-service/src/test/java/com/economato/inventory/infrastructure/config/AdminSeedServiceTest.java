package com.economato.inventory.infrastructure.config;

import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSeedServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminSeedService adminSeedService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminSeedService, "adminName", "Admin");
        ReflectionTestUtils.setField(adminSeedService, "adminUser", "admin");
        ReflectionTestUtils.setField(adminSeedService, "adminPassword", "admin123");
    }

    @Test
    void run_WhenNoAdminExists_ShouldCreateAdmin() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");

        adminSeedService.run();

        verify(userRepository).countByRole(Role.ADMIN);
        verify(passwordEncoder).encode("admin123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void run_WhenAdminExists_ShouldNotCreateAdmin() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        adminSeedService.run();

        verify(userRepository).countByRole(Role.ADMIN);
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
