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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class})
public class AuthControllerIntegrationTest {

    private static final String BASE_URL = "/api/v1/auth";

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
    public void whenLoginWithValidCredentials_thenSuccess() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("chefUser", "chef123");
        LoginResponseDTO loginResponse = new LoginResponseDTO("mocked.jwt.token", Role.CHEF);

        when(authUseCase.login(any(LoginRequestDTO.class))).thenReturn(loginResponse);

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").value("CHEF"));
    }

    @Test
    public void whenLoginWithInvalidCredentials_thenFail() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("usuarioinexistente", "contraseñaincorrecta");
        when(authUseCase.login(any(LoginRequestDTO.class))).thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenLoginWithEmptyUsername_thenBadRequest() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("", "chef123");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void whenLoginWithEmptyPassword_thenBadRequest() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO("chefUser", "");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void whenLoginWithMissingFields_thenBadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void whenValidateTokenWithValidToken_thenSuccess() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("username", "chefUser");

        when(authUseCase.validateToken(any())).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/validate").with(user("chefUser").roles("CHEF"))
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk());
    }

    @Test
    public void whenValidateTokenWithMissingToken_thenFail() throws Exception {
        mockMvc.perform(get(BASE_URL + "/validate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenLogoutWithoutToken_thenSuccess_Or_BadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/logout").with(user("chefUser").roles("CHEF")))
                .andExpect(status().isOk());
    }

    @Test
    public void whenGetRoleWithValidToken_thenSuccess() throws Exception {
        Map<String, String> response = new HashMap<>();
        response.put("role", "CHEF");

        when(authUseCase.getUserRole(any())).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/role").with(user("chefUser").roles("CHEF"))
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").value("CHEF"));
    }

    @Test
    public void whenGetRoleWithoutToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL + "/role"))
                .andExpect(status().isUnauthorized());
    }
}
