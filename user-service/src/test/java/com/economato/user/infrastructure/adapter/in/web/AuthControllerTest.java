package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.request.LoginRequestDTO;
import com.economato.user.application.dto.response.LoginResponseDTO;
import com.economato.user.application.port.in.AuthUseCase;
import com.economato.user.application.port.out.TokenBlacklistPort;
import com.economato.user.domain.model.Role;
import com.economato.user.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import com.economato.user.infrastructure.config.security.JwtFilter;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.security.SecurityConfig;
import com.economato.user.infrastructure.config.web.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthUseCase authUseCase;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockitoBean
    private JpaUserRepository jpaUserRepository;

    @MockitoBean
    private I18nService i18nService;

    @Test
    void login_WithValidCredentials_ShouldReturnOk() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("admin", "admin123");
        LoginResponseDTO response = new LoginResponseDTO("jwt-token-123", Role.ADMIN);

        when(authUseCase.login(any(LoginRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_WithEmptyUsername_ShouldReturnBadRequest() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("", "admin123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithEmptyPassword_ShouldReturnBadRequest() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("admin", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateToken_WhenAuthenticated_ShouldReturnOk() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("username", "admin");

        when(authUseCase.validateToken(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/auth/validate").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void validateToken_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserRole_WhenAuthenticated_ShouldReturnRole() throws Exception {
        Map<String, String> result = new HashMap<>();
        result.put("role", "CHEF");

        when(authUseCase.getUserRole(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/auth/role").with(user("chef").roles("CHEF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHEF"));
    }

    @Test
    void logout_WithValidHeader_ShouldReturnOk() throws Exception {
        Map<String, String> result = new HashMap<>();
        result.put("message", "Sesión cerrada exitosamente");

        when(authUseCase.logoutWithHeader(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/logout").with(user("user").roles("USER"))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));
    }
}
