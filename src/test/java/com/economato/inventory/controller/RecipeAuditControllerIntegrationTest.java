package com.economato.inventory.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.economato.inventory.dto.response.RecipeAuditResponseDTO;
import com.economato.inventory.service.RecipeAuditService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecipeAuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecipeAuditService recipeAuditService;

    private RecipeAuditResponseDTO testRecipeAudit;
    private List<RecipeAuditResponseDTO> testRecipeAudits;

    @BeforeEach
    void setUp() {
        testRecipeAudit = new RecipeAuditResponseDTO(
                1,
                1,
                "MODIFICACION",
                "Receta actualizada - cambio de costos",
                LocalDateTime.now());

        testRecipeAudits = Arrays.asList(testRecipeAudit);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAllRecipeAudits_ShouldReturnList() throws Exception {

        when(recipeAuditService.findAll(any(Pageable.class))).thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_recipe").value(1))
                .andExpect(jsonPath("$[0].id_user").value(1))
                .andExpect(jsonPath("$[0].action").value("MODIFICACION"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getRecipeAuditById_WhenExists_ShouldReturnAudit() throws Exception {

        when(recipeAuditService.findById(1)).thenReturn(Optional.of(testRecipeAudit));

        mockMvc.perform(get("/api/recipe-audits/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_recipe").value(1))
                .andExpect(jsonPath("$.id_user").value(1))
                .andExpect(jsonPath("$.action").value("MODIFICACION"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getRecipeAuditById_WhenNotExists_ShouldReturn404() throws Exception {

        when(recipeAuditService.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/recipe-audits/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAuditsByRecipeId_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByRecipeId(1)).thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-recipe/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_recipe").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAuditsByUserId_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByUserId(1)).thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-user/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_user").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAuditsByDateRange_ShouldReturnList() throws Exception {

        when(recipeAuditService.findByMovementDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(testRecipeAudits);

        mockMvc.perform(get("/api/recipe-audits/by-date-range")
                .param("start", "2026-01-01T00:00:00")
                .param("end", "2026-02-01T23:59:59")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id_recipe").value(1));
    }
}
