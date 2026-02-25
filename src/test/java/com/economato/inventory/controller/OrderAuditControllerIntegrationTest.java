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

import com.economato.inventory.dto.response.OrderAuditResponseDTO;
import com.economato.inventory.service.OrderAuditService;

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
class OrderAuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderAuditService orderAuditService;

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
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAllOrderAudits_ShouldReturnList() throws Exception {

        when(orderAuditService.findAll(any(Pageable.class))).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits")
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
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getOrderAuditById_WhenExists_ShouldReturnAudit() throws Exception {

        when(orderAuditService.findById(1)).thenReturn(Optional.of(testOrderAudit));

        mockMvc.perform(get("/api/order-audits/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.action").value("CAMBIO_ESTADO"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getOrderAuditById_WhenNotExists_ShouldReturn404() throws Exception {

        when(orderAuditService.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/order-audits/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getOrderAuditsByOrderId_ShouldReturnList() throws Exception {

        when(orderAuditService.findByOrderId(1)).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-order/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].orderId").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getOrderAuditsByUserId_ShouldReturnList() throws Exception {

        when(orderAuditService.findByUserId(1)).thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-user/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getOrderAuditsByDateRange_ShouldReturnList() throws Exception {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(orderAuditService.findByAuditDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(testOrderAudits);

        mockMvc.perform(get("/api/order-audits/by-date-range")
                .param("start", start.toString())
                .param("end", end.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
