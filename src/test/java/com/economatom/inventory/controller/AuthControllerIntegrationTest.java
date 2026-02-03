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
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

public class AuthControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/auth";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    public void setUp() {
        clearDatabase();

        testUser = TestDataUtil.createChefUser();
        testUser.setPassword(passwordEncoder.encode("chef123"));
        userRepository.save(testUser);
        userRepository.flush();
    }

    @Test
    public void whenLoginWithValidCredentials_thenSuccess() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(testUser.getName());
        loginRequest.setPassword("chef123");

        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    public void whenLoginWithInvalidCredentials_thenFail() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("usuarioinexistente");
        loginRequest.setPassword("contraseñaincorrecta");

        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenLoginWithEmptyUsername_thenBadRequest() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName("");
        loginRequest.setPassword("chef123");

        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void whenLoginWithEmptyPassword_thenBadRequest() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(testUser.getName());
        loginRequest.setPassword("");

        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
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

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(testUser.getName());
        loginRequest.setPassword("chef123");

        String response = mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);

        mockMvc.perform(get(BASE_URL + "/validate")
                .header("Authorization", "Bearer " + loginResponse.getToken()))
                .andExpect(status().isOk());
    }

    @Test
    public void whenValidateTokenWithInvalidToken_thenFail() throws Exception {
        mockMvc.perform(get(BASE_URL + "/validate")
                .header("Authorization", "Bearer invalidtoken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenValidateTokenWithMissingToken_thenFail() throws Exception {
        mockMvc.perform(get(BASE_URL + "/validate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenRegisterNewUser_thenSuccess() throws Exception {
        String registerRequest = "{\"name\":\"newuser\",\"password\":\"password123\",\"email\":\"newuser@test.com\",\"role\":\"USER\"}";

        mockMvc.perform(post(BASE_URL + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@test.com"));
    }
}