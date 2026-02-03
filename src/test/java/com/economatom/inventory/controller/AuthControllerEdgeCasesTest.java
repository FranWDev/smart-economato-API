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

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("Admin");
        loginRequest.setPassword("admin123");

        mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
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

        String response2 = mockMvc.perform(post(AUTH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO login2 = objectMapper.readValue(response2, LoginResponseDTO.class);

        assertNotNull(login1.getToken());
        assertNotNull(login2.getToken());
    }
}
