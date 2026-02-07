package com.economato.inventory.controller;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.economato.inventory.util.DatabaseCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private DatabaseCleaner databaseCleaner;

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
}
