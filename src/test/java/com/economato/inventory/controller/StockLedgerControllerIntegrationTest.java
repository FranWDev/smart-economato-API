package com.economato.inventory.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.StockSnapshot;
import com.economato.inventory.service.StockLedgerService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockLedgerControllerIntegrationTest extends BaseControllerMockTest {
    private StockLedger testLedger;
    private List<StockLedger> testLedgers;
    private StockSnapshot testSnapshot;
    private StockLedgerService.IntegrityCheckResult testIntegrityResult;

    @BeforeEach
    void setUp() {
        Product testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");

        testLedger = new StockLedger();
        testLedger.setId(1L);
        testLedger.setProduct(testProduct);
        testLedger.setSequenceNumber(1L);
        testLedger.setMovementType(com.economato.inventory.model.MovementType.ENTRADA);
        testLedger.setQuantityDelta(new BigDecimal("50"));
        testLedger.setResultingStock(new BigDecimal("50"));
        testLedger.setTransactionTimestamp(LocalDateTime.now());
        testLedger.setPreviousHash("genesis");
        testLedger.setCurrentHash("abc123");

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

        testIntegrityResult = new StockLedgerService.IntegrityCheckResult(
                true,
                "Cadena válida",
                Arrays.asList());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getProductHistory_ShouldReturnList() throws Exception {

        when(stockLedgerService.getProductHistory(1)).thenReturn(testLedgers);

        mockMvc.perform(get("/api/stock-ledger/history/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getProductHistory_WithAdminRole_ShouldReturnList() throws Exception {

        when(stockLedgerService.getProductHistory(anyInt())).thenReturn(testLedgers);

        mockMvc.perform(get("/api/stock-ledger/history/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void verifyAllChains_ShouldReturnList() throws Exception {

        when(stockLedgerService.verifyAllChains()).thenReturn(Arrays.asList(testIntegrityResult));

        mockMvc.perform(get("/api/stock-ledger/verify-all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].valid").value(true));
    }

    @Test
    @WithMockUser(username = "user", roles = { "USER" })
    void verifyAllChains_WithUserRole_ShouldReturnForbidden() throws Exception {

        mockMvc.perform(get("/api/stock-ledger/verify-all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getCurrentStock_WhenExists_ShouldReturnSnapshot() throws Exception {

        when(stockLedgerService.getCurrentStock(1)).thenReturn(Optional.of(testSnapshot));

        mockMvc.perform(get("/api/stock-ledger/snapshot/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.productName").value("Test Product"))
                .andExpect(jsonPath("$.integrityStatus").value("VALID"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getCurrentStock_WithAdminRole_ShouldReturnSnapshot() throws Exception {

        when(stockLedgerService.getCurrentStock(anyInt())).thenReturn(Optional.of(testSnapshot));

        mockMvc.perform(get("/api/stock-ledger/snapshot/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getAllSnapshots_ShouldReturnList() throws Exception {

        mockMvc.perform(get("/api/stock-ledger/snapshots")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void resetChain_ShouldReturnOk() throws Exception {

        when(stockLedgerService.resetProductLedger(1)).thenReturn("Historial restablecido");

        mockMvc.perform(delete("/api/stock-ledger/reset/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "chef", roles = { "CHEF" })
    void resetChain_WithChefRole_ShouldReturnForbidden() throws Exception {

        mockMvc.perform(delete("/api/stock-ledger/reset/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
