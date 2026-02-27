package com.economato.inventory.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import com.economato.inventory.dto.response.InventoryMovementResponseDTO;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InventoryAuditControllerIntegrationTest extends BaseControllerMockTest {
    private InventoryMovementResponseDTO testMovement;
    private List<InventoryMovementResponseDTO> testMovements;

    @BeforeEach
    void setUp() {
        testMovement = new InventoryMovementResponseDTO();
        testMovement.setId(1);
        testMovement.setProductId(1);
        testMovement.setProductName("Test Product");
        testMovement.setMovementType("ENTRADA");
        testMovement.setQuantity(new java.math.BigDecimal("50"));
        testMovement.setMovementDate(LocalDateTime.now());

        testMovements = Arrays.asList(testMovement);
    }

    @Test
    
    void getAllMovements_ShouldReturnList() throws Exception {

        when(inventoryAuditService.findAll(any(Pageable.class))).thenReturn(testMovements);

        mockMvc.perform(get("/api/inventory-audits").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].movementType").value("ENTRADA"))
                .andExpect(jsonPath("$[0].quantity").value(50));
    }

    @Test
    
    void getAllMovements_WithAdminRole_ShouldReturnList() throws Exception {

        when(inventoryAuditService.findAll(any(Pageable.class))).thenReturn(testMovements);

        mockMvc.perform(get("/api/inventory-audits").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    
    void getAllMovements_WithUserRole_ShouldReturnForbidden() throws Exception {
        // Este test debería retornar 403 pero retorna 500 debido a interacción entre
        // @MockBean y Spring Security
        mockMvc.perform(get("/api/inventory-audits").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    
    void getMovementById_WhenExists_ShouldReturnMovement() throws Exception {

        when(inventoryAuditService.findById(1)).thenReturn(Optional.of(testMovement));

        mockMvc.perform(get("/api/inventory-audits/1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.movementType").value("ENTRADA"));
    }

    @Test
    
    void getMovementById_WhenNotExists_ShouldReturn404() throws Exception {

        when(inventoryAuditService.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory-audits/999").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    
    void getMovementsByType_ShouldReturnList() throws Exception {

        when(inventoryAuditService.findByMovementType("ENTRADA")).thenReturn(testMovements);

        mockMvc.perform(get("/api/inventory-audits/type/ENTRADA").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].movementType").value("ENTRADA"));
    }

    @Test
    
    void getMovementsByType_WithAdminRole_ShouldReturnList() throws Exception {

        when(inventoryAuditService.findByMovementType(anyString())).thenReturn(testMovements);

        mockMvc.perform(get("/api/inventory-audits/type/SALIDA").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    
    void getMovementsByDateRange_ShouldReturnList() throws Exception {

        when(inventoryAuditService.findByMovementDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(testMovements);

        mockMvc.perform(get("/api/inventory-audits/by-date-range").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .param("start", "2026-01-01T00:00:00")
                .param("end", "2026-02-01T23:59:59")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));
    }
}
