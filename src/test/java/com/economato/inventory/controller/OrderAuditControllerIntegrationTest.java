package com.economato.inventory.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import com.economato.inventory.dto.response.OrderAuditResponseDTO;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderAuditControllerIntegrationTest extends BaseControllerMockTest {
    private OrderAuditResponseDTO testOrderAudit;
    private List<OrderAuditResponseDTO> testOrderAudits;

    @BeforeEach
    void setUp() {
        testOrderAudit = new OrderAuditResponseDTO();
        testOrderAudit.setId(1);
        testOrderAudit.setOrderId(1);
        testOrderAudit.setUserId(1);
        testOrderAudit.setUserName("Test User");
        testOrderAudit.setAction("CAMBIO_ESTADO");
        testOrderAudit.setDetails("Estado cambiado de CREATED a PENDING");
        testOrderAudit.setPreviousState("{\"status\":\"CREATED\"}");
        testOrderAudit.setNewState("{\"status\":\"PENDING\"}");
        testOrderAudit.setAuditDate(LocalDateTime.now());

        testOrderAudits = Arrays.asList(testOrderAudit);
    }

    @Test
    
    void getAllOrderAudits_ShouldReturnList() throws Exception {

        when(orderAuditService.findAll(any(Pageable.class))).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].action").value("CAMBIO_ESTADO"));
    }

    @Test
    
    void getOrderAuditById_WhenExists_ShouldReturnAudit() throws Exception {

        when(orderAuditService.findById(1)).thenReturn(Optional.of(testOrderAudit));

        mockMvc.perform(get("/api/order-audits/1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.action").value("CAMBIO_ESTADO"));
    }

    @Test
    
    void getOrderAuditById_WhenNotExists_ShouldReturn404() throws Exception {

        when(orderAuditService.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/order-audits/999").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    
    void getOrderAuditsByOrderId_ShouldReturnList() throws Exception {

        when(orderAuditService.findByOrderId(1)).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-order/1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].orderId").value(1));
    }

    @Test
    
    void getOrderAuditsByUserId_ShouldReturnList() throws Exception {

        when(orderAuditService.findByUserId(1)).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-user/1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    
    void getOrderAuditsByDateRange_ShouldReturnList() throws Exception {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(orderAuditService.findByAuditDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-date-range").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .param("start", start.toString())
                .param("end", end.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
