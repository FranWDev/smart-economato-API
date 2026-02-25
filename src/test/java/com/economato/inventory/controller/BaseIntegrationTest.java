package com.economato.inventory.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.economato.inventory.service.CustomUserDetailsService;
import com.economato.inventory.util.DatabaseCleaner;
import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @BeforeAll
    static void configureSecurityContextPropagation() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    protected CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void clearUserDetailsCache() {
        customUserDetailsService.clearCache();
    }

    protected String asJsonString(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            if (json == null || json.isBlank()) {
                throw new RuntimeException("El JSON generado está vacío");
            }
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar objeto a JSON", e);
        }
    }

    protected void clearDatabase() {
        databaseCleaner.clear();
    }

    protected String login(String username, String password) throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setName(username);
        loginRequest.setPassword(password);

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
        return loginResponse.getToken();
    }

    protected String loginAsAdmin() throws Exception {
        return login("Admin", "admin123");
    }
}
