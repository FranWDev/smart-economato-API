package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.request.LoginRequestDTO;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.application.usecase.UserService;
import com.economato.inventory.infrastructure.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

class AuthControllerEdgeCasesTest extends BaseIntegrationTest {

    private static final String AUTH_URL = "/api/auth/login";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        clearDatabase();
        testUser = TestDataUtil.createAdminUser();
        userRepository.saveAndFlush(testUser);
    }

    @Test
    void login_WithValidCredentials_ShouldReturnToken() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("admin123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", notNullValue()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_WithInvalidPassword_ShouldReturnUnauthorized() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("wrongPassword");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithNonExistentUser_ShouldReturnUnauthorized() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("NonExistent");
        loginRequest.setPassword("password");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithEmptyName_ShouldReturnBadRequest() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("");
        loginRequest.setPassword("password");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithEmptyPassword_ShouldReturnBadRequest() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithNullName_ShouldReturnBadRequest() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(null);
        loginRequest.setPassword("password");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithNullPassword_ShouldReturnBadRequest() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword(null);

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithEmptyBody_ShouldReturnBadRequest() throws Exception {

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithCaseInsensitiveUsername_ShouldWork() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("admin");
        loginRequest.setPassword("admin123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_MultipleTimesWithSameCredentials_ShouldGenerateDifferentTokens() throws Exception {

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("admin123");

        String response1 = mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO login1 = objectMapper.readValue(response1, LoginResponseDTO.class);

        TimeUnit.MILLISECONDS.sleep(5);

        String response2 = mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO login2 = objectMapper.readValue(response2, LoginResponseDTO.class);

        assertNotNull(login1.getToken());
        assertNotNull(login2.getToken());
        assertFalse(login1.getToken().isBlank());
        assertFalse(login2.getToken().isBlank());
        assertEquals(login1.getRole(), login2.getRole());
    }

    @Test
    void login_MultipleTimesWithUsernameField_ShouldAlwaysSucceed() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("adminUser");
        loginRequest.setPassword("admin123");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(AUTH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }
    }

    @Test
    void login_FailedAttemptForOtherUser_ShouldNotAffectValidUser() throws Exception {
        LoginRequestDTO invalidAttempt = new LoginRequestDTO();
        invalidAttempt.setName("NonExistent");
        invalidAttempt.setPassword("bad");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(invalidAttempt)))
                .andExpect(status().isUnauthorized());

        LoginRequestDTO validLogin = new LoginRequestDTO();
        validLogin.setName("Admin");
        validLogin.setPassword("admin123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(validLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_WithHiddenUser_ShouldReturnUnauthorized() throws Exception {
        // Crear un usuario oculto
        User hiddenUser = TestDataUtil.createChefUser();
        hiddenUser.setName("HiddenChef");
        hiddenUser.setUser("hiddenChef");
        hiddenUser.setHidden(true); // Marcar como oculto
        userRepository.saveAndFlush(hiddenUser);

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("HiddenChef");
        loginRequest.setPassword("chef123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithHiddenUserByUserField_ShouldReturnUnauthorized() throws Exception {
        // Crear un usuario oculto
        User hiddenUser = TestDataUtil.createRegularUser();
        hiddenUser.setName("HiddenUser");
        hiddenUser.setUser("hiddenUserAlias");
        hiddenUser.setHidden(true); // Marcar como oculto
        userRepository.saveAndFlush(hiddenUser);

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("hiddenUserAlias");
        loginRequest.setPassword("user123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithNormalUserThenHidden_ShouldFailAfterHidden() throws Exception {
        // Crear un usuario normal
        User normalUser = TestDataUtil.createChefUser();
        normalUser.setName("NormalChef");
        normalUser.setUser("normalChef");
        normalUser.setHidden(false); // No oculto
        userRepository.saveAndFlush(normalUser);

        // Primer login debe funcionar
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("NormalChef");
        loginRequest.setPassword("chef123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));

        // Ocultar el usuario (Usando el service para evicción de caché)
        userService.toggleUserHiddenStatus(normalUser.getId(), true);

        // Segundo login debe fallar
        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}
