package com.economato.inventory.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.response.StockLedgerResponseDTO;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.infrastructure.config.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.security.JwtUtils;
import com.economato.inventory.application.usecase.TokenBlacklistService;
import com.economato.inventory.infrastructure.config.web.I18nService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockLedgerController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class StockLedgerControllerIntegrationTest {
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

    private StockLedger testLedger;
    private List<StockLedger> testLedgers;
    private StockSnapshot testSnapshot;
    private IntegrityCheckResult testIntegrityResult;

    @BeforeEach
    void setUp() {
        Product testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");

        testLedger = StockLedger.builder()
                .id(1L)
                .product(testProduct)
                .sequenceNumber(1L)
                .movementType(MovementType.ENTRADA)
                .quantityDelta(new BigDecimal("50"))
                .resultingStock(new BigDecimal("50"))
                .transactionTimestamp(LocalDateTime.now())
                .previousHash("genesis")
                .currentHash("abc123")
                .build();

        testLedgers = Arrays.asList(testLedger);

        testSnapshot = new StockSnapshot();
        testSnapshot.setProductId(1);
        testSnapshot.setProduct(testProduct);
        testSnapshot.setCurrentStock(new BigDecimal("50"));
        testSnapshot.setLastSequenceNumber(1L);
        testSnapshot.setLastTransactionHash("abc123");
        testSnapshot.setIntegrityStatus("VALID");
        testSnapshot.setLastUpdated(LocalDateTime.now());
        testSnapshot.setLastVerified(LocalDateTime.now());

        testIntegrityResult = new IntegrityCheckResult(
                1,
                "Test Product",
                true,
                "Cadena válida",
                Arrays.asList());
    }

    @Test

    void getProductHistory_ShouldReturnList() throws Exception {

        Page<StockLedger> page = new PageImpl<>(testLedgers, PageRequest.of(0, 20), 1);
        StockLedgerResponseDTO dto = StockLedgerResponseDTO.builder()
                .id(1L)
                .productId(1)
                .build();
        when(stockLedgerService.getProductHistory(anyInt(), any())).thenReturn(page);
        when(stockLedgerMapper.toDTO(any(StockLedger.class))).thenReturn(dto);

        mockMvc.perform(get("/api/stock-ledger/history/1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].productId").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test

    void getProductHistory_WithAdminRole_ShouldReturnList() throws Exception {

                Page<StockLedger> page = new PageImpl<>(testLedgers, PageRequest.of(0, 10), 1);
        StockLedgerResponseDTO dto = StockLedgerResponseDTO.builder()
                .id(1L)
                .productId(1)
                .build();
        when(stockLedgerService.getProductHistory(anyInt(), any())).thenReturn(page);
        when(stockLedgerMapper.toDTO(any(StockLedger.class))).thenReturn(dto);

        mockMvc.perform(get("/api/stock-ledger/history/1?page=0&size=10")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test

    void verifyAllChains_ShouldReturnList() throws Exception {

        when(stockLedgerService.verifyAllChains()).thenReturn(Arrays.asList(testIntegrityResult));

        mockMvc.perform(get("/api/stock-ledger/verify-all")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].valid").value(true));
    }

    @Test

    void verifyAllChains_WithUserRole_ShouldReturnForbidden() throws Exception {

        mockMvc.perform(get("/api/stock-ledger/verify-all")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test

    void getCurrentStock_WhenExists_ShouldReturnSnapshot() throws Exception {

        when(stockLedgerService.getCurrentStock(1)).thenReturn(Optional.of(testSnapshot));

        mockMvc.perform(get("/api/stock-ledger/snapshot/1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.productName").value("Test Product"))
                .andExpect(jsonPath("$.integrityStatus").value("VALID"));
    }

    @Test

    void getCurrentStock_WithAdminRole_ShouldReturnSnapshot() throws Exception {

        when(stockLedgerService.getCurrentStock(anyInt())).thenReturn(Optional.of(testSnapshot));

        mockMvc.perform(get("/api/stock-ledger/snapshot/1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
