package com.economato.user.application.service;

import com.economato.user.application.dto.request.LoginRequestDTO;
import com.economato.user.application.dto.response.LoginResponseDTO;
import com.economato.user.application.port.out.TokenBlacklistPort;
import com.economato.user.domain.model.Role;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.web.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Mock
    private I18nService i18nService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenBlacklistPort tokenBlacklistPort;

    @Mock
    private Authentication authentication;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(i18nService, authenticationManager, jwtUtils, tokenBlacklistPort);
    }

    @Test
    void testLogin_Success() {
        LoginRequestDTO request = new LoginRequestDTO("admin", "password123");
        LoginResponseDTO expectedResponse = new LoginResponseDTO("mocked-jwt-token", Role.ADMIN);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn(expectedResponse);

        LoginResponseDTO actualResponse = authService.login(request);

        assertNotNull(actualResponse);
        assertEquals("mocked-jwt-token", actualResponse.getToken());
        assertEquals(Role.ADMIN, actualResponse.getRole());
    }

    @Test
    void testValidateToken_Success() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");

        Map<String, Object> result = authService.validateToken(authentication);

        assertNotNull(result);
        assertEquals(true, result.get("valid"));
        assertEquals("admin", result.get("username"));
    }
}
