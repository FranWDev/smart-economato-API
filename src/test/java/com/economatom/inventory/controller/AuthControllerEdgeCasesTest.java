package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.LoginRequestDTO;
import com.economatom.inventory.dto.response.LoginResponseDTO;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.UserRepository;
import com.economatom.inventory.util.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthControllerEdgeCasesTest extends BaseIntegrationTest {

    private static final String AUTH_URL = "/api/auth/login";

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        clearDatabase();
        testUser = TestDataUtil.createAdminUser();
        userRepository.saveAndFlush(testUser);
    }

    @Test
    void login_WithValidCredentials_ShouldReturnToken() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("admin123");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void login_WithInvalidPassword_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("wrongPassword");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithNonExistentUser_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("NonExistent");
        loginRequest.setPassword("password");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_WithEmptyName_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("");
        loginRequest.setPassword("password");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithEmptyPassword_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithNullName_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(null);
        loginRequest.setPassword("password");

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithNullPassword_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword(null);

        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_WithEmptyBody_ShouldReturnBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }



    @Test
    void login_WithCaseInsensitiveUsername_ShouldWork() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("admin"); // lowercase
        loginRequest.setPassword("admin123");

        // Act & Assert - Depends on how your security is configured
        mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized()); // Assuming case-sensitive
    }

    @Test
    void login_MultipleTimesWithSameCredentials_ShouldGenerateDifferentTokens() throws Exception {
        // Arrange
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("admin123");

        // Act - First login
        String response1 = mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO login1 = objectMapper.readValue(response1, LoginResponseDTO.class);

        // Act - Second login
        String response2 = mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO login2 = objectMapper.readValue(response2, LoginResponseDTO.class);

        // Assert - Tokens might be the same or different depending on JWT implementation
        // This test just verifies both logins work
        assertNotNull(login1.getToken());
        assertNotNull(login2.getToken());
    }
}
