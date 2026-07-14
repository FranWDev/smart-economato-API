package com.economato.inventory.infrastructure.adapter.in.web.ledger;

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

import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResponseDTO;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.dto.stock.response.StockSnapshotResponseDTO;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.infrastructure.config.shared.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;

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
        StockLedgerResponseDTO dto = StockLedgerResponseDTO.builder()
                .id(1L)
                .productId(1)
                .build();
        Page<StockLedgerResponseDTO> page = new PageImpl<>(Arrays.asList(dto), PageRequest.of(0, 20), 1);
        when(stockLedgerService.getProductHistoryDto(anyInt(), any())).thenReturn(page);

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
        StockLedgerResponseDTO dto = StockLedgerResponseDTO.builder()
                .id(1L)
                .productId(1)
                .build();
        Page<StockLedgerResponseDTO> page = new PageImpl<>(Arrays.asList(dto), PageRequest.of(0, 10), 1);
        when(stockLedgerService.getProductHistoryDto(anyInt(), any())).thenReturn(page);

        mockMvc.perform(get("/api/stock-ledger/history/1?page=0&size=10")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void verifyAllChains_ShouldReturnList() throws Exception {
        IntegrityCheckResponseDTO response = IntegrityCheckResponseDTO.builder()
                .valid(true)
                .build();
        when(stockLedgerService.verifyAllChainsDto()).thenReturn(Arrays.asList(response));

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
        StockSnapshotResponseDTO response = StockSnapshotResponseDTO.builder()
                .productId(1)
                .productName("Test Product")
                .integrityStatus("VALID")
                .build();
        when(stockLedgerService.getCurrentStockDto(1)).thenReturn(response);

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
        StockSnapshotResponseDTO response = StockSnapshotResponseDTO.builder()
                .productId(1)
                .productName("Test Product")
                .integrityStatus("VALID")
                .build();
        when(stockLedgerService.getCurrentStockDto(anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/stock-ledger/snapshot/1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
