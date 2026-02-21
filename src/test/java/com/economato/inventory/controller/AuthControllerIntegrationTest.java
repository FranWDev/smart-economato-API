package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.service.TokenBlacklistService;
import com.economato.inventory.util.TestDataUtil;

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

        @Autowired
        private TokenBlacklistService tokenBlacklistService;

        private User testUser;

        @BeforeEach
        public void setUp() {
                clearDatabase();

                tokenBlacklistService.clearBlacklist();

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
                                .andExpect(jsonPath("$.token").value(not(emptyString())))
                                .andExpect(jsonPath("$.role").exists())
                                .andExpect(jsonPath("$.role").value("CHEF"));
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
        public void whenLogoutWithValidToken_thenSuccess() throws Exception {

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testUser.getName());
                loginRequest.setPassword("chef123");

                String response = mockMvc.perform(post(BASE_URL + "/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                String token = loginResponse.getToken();

                mockMvc.perform(post(BASE_URL + "/logout")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));

                mockMvc.perform(get(BASE_URL + "/validate")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenLogoutWithoutToken_thenBadRequest() throws Exception {
                mockMvc.perform(post(BASE_URL + "/logout"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenLogoutWithInvalidToken_thenBadRequest() throws Exception {
                mockMvc.perform(post(BASE_URL + "/logout")
                                .header("Authorization", "Bearer invalidtoken"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenLogoutWithMalformedAuthHeader_thenBadRequest() throws Exception {
                mockMvc.perform(post(BASE_URL + "/logout")
                                .header("Authorization", "InvalidFormat token"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenGetRoleWithValidToken_thenSuccess() throws Exception {
                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testUser.getName());
                loginRequest.setPassword("chef123");

                String response = mockMvc.perform(post(BASE_URL + "/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);

                mockMvc.perform(get(BASE_URL + "/role")
                                .header("Authorization", "Bearer " + loginResponse.getToken()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").exists())
                                .andExpect(jsonPath("$.role").value("CHEF"));
        }

        @Test
        public void whenGetRoleWithInvalidToken_thenUnauthorized() throws Exception {
                mockMvc.perform(get(BASE_URL + "/role")
                                .header("Authorization", "Bearer invalidtoken"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenGetRoleWithoutToken_thenUnauthorized() throws Exception {
                mockMvc.perform(get(BASE_URL + "/role"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenUsingBlacklistedTokenForProtectedEndpoint_thenUnauthorized() throws Exception {

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testUser.getName());
                loginRequest.setPassword("chef123");

                String response = mockMvc.perform(post(BASE_URL + "/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                String token = loginResponse.getToken();

                mockMvc.perform(get(BASE_URL + "/validate")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk());

                mockMvc.perform(post(BASE_URL + "/logout")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL + "/validate")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isUnauthorized());
        }
}