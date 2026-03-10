package com.economato.inventory.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockConsumptionIntegrationTest extends BaseControllerMockTest {

    private Product testProduct;
    private ProductConsumptionResponseDTO testResponse;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setUnit("kg");

        testResponse = ProductConsumptionResponseDTO.builder()
                .productId(1)
                .productName("Test Product")
                .totalConsumed(new BigDecimal("15.500"))
                .unit("kg")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now())
                .build();
    }

    @Test
    void getProductConsumption_WithDate_ShouldReturnConsumption() throws Exception {
        LocalDate date = LocalDate.of(2024, 5, 3);
        when(stockLedgerService.getProductConsumption(eq(1), any(), any())).thenReturn(testResponse);

        mockMvc.perform(get("/api/stock-ledger/consumption/1")
                .param("date", "2024-05-03")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.totalConsumed").value(15.5));
    }

    @Test
    void getProductConsumption_WithLastDays_ShouldReturnConsumption() throws Exception {
        when(stockLedgerService.getProductConsumption(eq(1), any(), any())).thenReturn(testResponse);

        mockMvc.perform(get("/api/stock-ledger/consumption/1")
                .param("lastDays", "5")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void getProductConsumption_WithDateRange_ShouldReturnConsumption() throws Exception {
        when(stockLedgerService.getProductConsumption(eq(1), any(), any())).thenReturn(testResponse);

        mockMvc.perform(get("/api/stock-ledger/consumption/1")
                .param("startDate", "2024-05-01T00:00:00")
                .param("endDate", "2024-05-10T23:59:59")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void getProductConsumption_ProductNotFound_ShouldReturnError() throws Exception {
        when(stockLedgerService.getProductConsumption(eq(999), any(), any()))
                .thenThrow(new InvalidOperationException("Product with ID 999 not found"));

        mockMvc.perform(get("/api/stock-ledger/consumption/999")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product with ID 999 not found"));
    }

    @Test
    void getProductConsumption_InvalidDateRange_ShouldReturnError() throws Exception {
        when(stockLedgerService.getProductConsumption(eq(1), any(), any()))
                .thenThrow(new InvalidOperationException("Start date cannot be after end date"));

        mockMvc.perform(get("/api/stock-ledger/consumption/1")
                .param("startDate", "2024-05-10T00:00:00")
                .param("endDate", "2024-05-01T23:59:59")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start date cannot be after end date"));
    }
}
