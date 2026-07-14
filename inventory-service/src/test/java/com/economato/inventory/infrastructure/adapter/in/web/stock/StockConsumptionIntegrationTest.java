package com.economato.inventory.infrastructure.adapter.in.web.stock;
import com.economato.inventory.infrastructure.adapter.in.web.ledger.StockLedgerController;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

import java.util.ArrayList;
import java.util.List;

import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.infrastructure.config.shared.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockLedgerController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class StockConsumptionIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private StockLedgerService stockLedgerService;

        @MockitoBean
        private StockLedgerMapper stockLedgerMapper;

        @MockitoBean
        private ProductBatchService productBatchService;

        @MockitoBean
        private JwtUtils jwtUtils;

        @MockitoBean
        private UserDetailsService userDetailsService;

        @MockitoBean
        private TokenBlacklistService tokenBlacklistService;

        @MockitoBean
        private I18nService i18nService;

        @MockitoBean
        private LocaleResolver localeResolver;

        @MockitoBean
        private CacheManager cacheManager;

    private Product testProduct;
    private ProductConsumptionResponseDTO testResponse;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setUnit("kg");

        List<ProductConsumptionResponseDTO.DailyConsumptionDTO> breakdown = new ArrayList<>();
        breakdown.add(new ProductConsumptionResponseDTO.DailyConsumptionDTO(LocalDate.of(2024, 5, 3), new BigDecimal("15.500")));

        testResponse = ProductConsumptionResponseDTO.builder()
                .productId(1)
                .productName("Test Product")
                .breakdown(breakdown)
                .unit("kg")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now())
                .build();
    }

    @Test
    void getProductConsumption_WithDate_ShouldReturnConsumption() throws Exception {
        when(stockLedgerService.getProductConsumptionDto(eq(1), any(), any(), any(), any())).thenReturn(testResponse);

        mockMvc.perform(get("/api/stock-ledger/consumption/1")
                .param("date", "2024-05-03")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.breakdown[0].date").value("2024-05-03"))
                .andExpect(jsonPath("$.breakdown[0].consumed").value(15.5));
    }

    @Test
    void getProductConsumption_WithLastDays_ShouldReturnConsumption() throws Exception {
        when(stockLedgerService.getProductConsumptionDto(eq(1), any(), any(), any(), any())).thenReturn(testResponse);

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
        when(stockLedgerService.getProductConsumptionDto(eq(1), any(), any(), any(), any())).thenReturn(testResponse);

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
        when(stockLedgerService.getProductConsumptionDto(eq(999), any(), any(), any(), any()))
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
        when(stockLedgerService.getProductConsumptionDto(eq(1), any(), any(), any(), any()))
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
