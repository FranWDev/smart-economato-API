package com.economato.inventory.infrastructure.adapter.in.web.blockchain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.servlet.LocaleResolver;

import com.economato.inventory.application.dto.blockchain.response.BlockchainStatsResponseDTO;
import com.economato.inventory.application.dto.blockchain.response.BlockchainVerificationResponseDTO;
import com.economato.inventory.application.dto.ledger.response.LedgerBlockResponseDTO;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.usecase.blockchain.BlockchainService;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import com.economato.inventory.domain.model.ledger.LedgerBlock;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.infrastructure.config.shared.security.SecurityConfig;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@WebMvcTest(BlockchainAdminController.class)
@ActiveProfiles({"test", "kafka-test"})
@Import(SecurityConfig.class)
class BlockchainAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockchainService blockchainService;

    @MockitoBean
    private StockLedgerService stockLedgerService;

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

    private LedgerBlock latestBlock;
    private StockLedger pendingLedger;

    @BeforeEach
    void setUp() {
        latestBlock = LedgerBlock.builder()
                .id(10L)
                .blockNumber(5L)
                .previousBlockHash("0000000000000000000000000000000000000000000000000000000000000001")
                .merkleRoot("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .blockHash("0000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .timestamp(LocalDateTime.of(2026, 4, 10, 10, 15, 0))
                .transactionCount(10)
                .hmacKeyVersion(1)
                .build();

        pendingLedger = StockLedger.builder()
                .id(1L)
                .sequenceNumber(1L)
                .currentHash("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")
                .previousHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .transactionTimestamp(LocalDateTime.of(2026, 4, 10, 10, 5, 0))
                .build();
    }

    @Test
    void verifyBlockchain_returnsSummary() throws Exception {
        BlockchainVerificationResponseDTO response = BlockchainVerificationResponseDTO.builder()
                .valid(true)
                .message("Blockchain íntegra")
                .blockCount(6L)
                .pendingTransactions(2L)
                .latestBlockNumber(5L)
                .latestBlockHash(latestBlock.getBlockHash())
                .build();

        when(blockchainService.verifyBlockchain()).thenReturn(response);

        mockMvc.perform(get("/api/admin/blockchain/verify")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.blockCount").value(6))
                .andExpect(jsonPath("$.pendingTransactions").value(2))
                .andExpect(jsonPath("$.latestBlockNumber").value(5))
                .andExpect(jsonPath("$.latestBlockHash").value(latestBlock.getBlockHash()));
    }

    @Test
    void getStats_returnsBlockchainStats() throws Exception {
        BlockchainStatsResponseDTO stats = BlockchainStatsResponseDTO.builder()
                .blockCount(6L)
                .pendingTransactions(2L)
                .latestBlockNumber(5L)
                .latestBlockHash(latestBlock.getBlockHash())
                .valid(true)
                .build();

        when(blockchainService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/blockchain/stats")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockCount").value(6))
                .andExpect(jsonPath("$.pendingTransactions").value(2))
                .andExpect(jsonPath("$.latestBlockNumber").value(5))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void getBlocks_returnsPageOfBlocks() throws Exception {
        LedgerBlockResponseDTO blockDto = LedgerBlockResponseDTO.builder()
                .blockNumber(5L)
                .blockHash(latestBlock.getBlockHash())
                .build();
        Page<LedgerBlockResponseDTO> pageDto = new PageImpl<>(List.of(blockDto), PageRequest.of(0, 10), 1);

        when(blockchainService.getBlocks(any(Pageable.class))).thenReturn(pageDto);

        mockMvc.perform(get("/api/admin/blockchain/blocks")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].blockNumber").value(5))
                .andExpect(jsonPath("$.content[0].blockHash").value(latestBlock.getBlockHash()));
    }

    @Test
    void getBlock_byNumber_returnsBlock() throws Exception {
        LedgerBlockResponseDTO blockDto = LedgerBlockResponseDTO.builder()
                .blockNumber(5L)
                .merkleRoot(latestBlock.getMerkleRoot())
                .transactionCount(10)
                .build();

        when(blockchainService.getBlock(5L)).thenReturn(Optional.of(blockDto));

        mockMvc.perform(get("/api/admin/blockchain/blocks/5")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockNumber").value(5))
                .andExpect(jsonPath("$.merkleRoot").value(latestBlock.getMerkleRoot()))
                .andExpect(jsonPath("$.transactionCount").value(10));
    }

    @Test
    void getBlock_whenMissing_returns404() throws Exception {
        when(blockchainService.getBlock(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/blockchain/blocks/999")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMempool_returnsPendingTransactionsPage() throws Exception {
        StockLedgerResponseDTO dto = StockLedgerResponseDTO.builder()
                .id(1L)
                .sequenceNumber(1L)
                .currentHash(pendingLedger.getCurrentHash())
                .previousHash(pendingLedger.getPreviousHash())
                .build();
        Page<StockLedgerResponseDTO> pageDto = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(blockchainService.getMempool(any(Pageable.class))).thenReturn(pageDto);

        mockMvc.perform(get("/api/admin/blockchain/mempool")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].currentHash").value(pendingLedger.getCurrentHash()));
    }

    @Test
    void adminEndpoints_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/blockchain/verify")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void syncStockWithLedger_withAdminRole_returnsSuccess() throws Exception {
        when(i18nService.getMessage(MessageKey.SUCCESS_BLOCKCHAIN_SYNC))
                .thenReturn("Stock y lotes sincronizados correctamente con el Ledger");

        mockMvc.perform(post("/api/admin/blockchain/sync-stock")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Stock y lotes sincronizados correctamente con el Ledger"));
    }
}
