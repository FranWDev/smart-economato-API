package com.economato.inventory.application.usecase.user;
import com.economato.inventory.application.usecase.notification.RoleNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;

import com.economato.inventory.application.dto.shared.request.ChangePasswordRequestDTO;
import com.economato.inventory.application.dto.user.request.RoleEscalationRequestDTO;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.TemporaryRoleEscalationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.application.mapper.shared.StatsMapper;
import com.economato.inventory.application.mapper.user.TemporaryRoleEscalationMapper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceConfigValidationTest {

    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private TemporaryRoleEscalationRepository escalationRepository;
    @Mock private TemporaryRoleEscalationMapper escalationMapper;
    @Mock private StatsMapper statsMapper;
    @Mock private I18nService i18nService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private RoleNotificationService roleNotificationService;

    private UserService service;

    private void init() throws Exception {
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(inv -> inv.getArgument(0, MessageKey.class).getKey());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0, MessageKey.class).getKey());
        lenient().when(systemConfigService.getMinPasswordLength()).thenReturn(6);
        lenient().when(systemConfigService.getMaxEscalationMinutes()).thenReturn(1440);

        service = new UserService(
                i18nService,
                repository,
                passwordEncoder,
                null,
                escalationMapper,
                statsMapper,
                customUserDetailsService,
                escalationRepository,
                roleNotificationService
        );
        setField(service, "systemConfigService", systemConfigService);
    }

    @Test
    void changePassword_WhenTooShort_ShouldThrow() {
        try { init(); } catch (Exception e) { throw new RuntimeException(e); }
        User user = new User();
        user.setId(1);
        user.setName("User");
        user.setUser("user");
        user.setPassword("encoded");
        when(repository.findById(1)).thenReturn(Optional.of(user));
        when(systemConfigService.getMinPasswordLength()).thenReturn(8);

        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setNewPassword("short6");

        var thrown = assertThrows(InvalidOperationException.class, () -> service.changePassword(1, request, false, false));
        assertNotNull(thrown);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void escalateRole_WhenDurationExceedsMax_ShouldThrow() {
        try { init(); } catch (Exception e) { throw new RuntimeException(e); }
        User user = new User();
        user.setId(1);
        user.setName("User");
        user.setUser("user");
        user.setRole(Role.USER);
        when(repository.findById(1)).thenReturn(Optional.of(user));
        when(systemConfigService.getMaxEscalationMinutes()).thenReturn(30);

        RoleEscalationRequestDTO request = new RoleEscalationRequestDTO();
        request.setDurationMinutes(60);

        var thrown = assertThrows(InvalidOperationException.class, () -> service.escalateRole(1, request));
        assertNotNull(thrown);
        verify(escalationRepository, never()).save(any());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
