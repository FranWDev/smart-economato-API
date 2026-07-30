package com.economato.user.application.service;

import com.economato.user.application.dto.request.ChangePasswordRequestDTO;
import com.economato.user.application.dto.request.UserRequestDTO;
import com.economato.user.application.dto.response.UserResponseDTO;
import com.economato.user.application.port.out.UserEventPublisherPort;
import com.economato.user.application.port.out.UserRepositoryPort;
import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.web.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private UserAccessPolicy accessPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private I18nService i18nService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserEventPublisherPort eventPublisher;

    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepositoryPort, accessPolicy, passwordEncoder, i18nService, jwtUtils, eventPublisher);

        testUser = new User();
        testUser.setId(1);
        testUser.setName("Juan Perez");
        testUser.setUser("juanperez");
        testUser.setPassword("encodedPassword");
        testUser.setRole(Role.USER);
        testUser.setFirstLogin(false);
        testUser.setHidden(false);
    }

    @Test
    void testFindAll() {
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepositoryPort.findByIsHidden(false, PageRequest.of(0, 10))).thenReturn(page);

        Page<UserResponseDTO> result = userService.findAll(PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Juan Perez", result.getContent().get(0).getName());
    }

    @Test
    void testFindById_Success() {
        when(userRepositoryPort.findById(1)).thenReturn(Optional.of(testUser));

        Optional<UserResponseDTO> result = userService.findById(1);

        assertTrue(result.isPresent());
        assertEquals("juanperez", result.get().getUser());
    }

    @Test
    void testSave_Success() {
        UserRequestDTO request = new UserRequestDTO("Juan Perez", "juanperez", "secret123", Role.USER, null);

        when(userRepositoryPort.existsByUser("juanperez")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encodedSecret");
        when(userRepositoryPort.save(any(User.class))).thenReturn(testUser);

        UserResponseDTO result = userService.save(request);

        assertNotNull(result);
        assertEquals("Juan Perez", result.getName());
        verify(eventPublisher).publishUserCreated(any());
    }

    @Test
    void testSave_DuplicateUser_ThrowsException() {
        UserRequestDTO request = new UserRequestDTO("Juan Perez", "juanperez", "secret123", Role.USER, null);

        when(userRepositoryPort.existsByUser("juanperez")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.save(request));
    }

    @Test
    void testDeleteById_Success() {
        when(userRepositoryPort.findById(1)).thenReturn(Optional.of(testUser));

        userService.deleteById(1);

        verify(userRepositoryPort).deleteById(1);
        verify(eventPublisher).publishUserDeleted(any());
    }

    @Test
    void testChangePassword_Success() {
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO("oldPass", "newPass123");
        when(userRepositoryPort.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPass123")).thenReturn("encodedNewPass");

        userService.changePassword(1, request);

        verify(userRepositoryPort).save(testUser);
        assertFalse(testUser.isFirstLogin());
    }
}
