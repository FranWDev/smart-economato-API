package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.response.BlockchainStatsResponseDTO;
import com.economato.inventory.application.dto.response.BlockchainVerificationResponseDTO;
import com.economato.inventory.application.dto.response.LedgerBlockResponseDTO;
import com.economato.inventory.application.dto.response.StockLedgerResponseDTO;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.application.usecase.BlockchainService;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.model.LedgerBlock;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blockchain")
@RequiredArgsConstructor
@Profile({"!test", "kafka-test"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Blockchain Admin", description = "Verificación y exploración del ledger blockchain")
public class BlockchainAdminController {

    private final BlockchainService blockchainService;
    private final StockLedgerService stockLedgerService;
    private final LedgerBlockRepository ledgerBlockRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final StockLedgerMapper stockLedgerMapper;

    @GetMapping("/verify")
    @Operation(summary = "Verificar blockchain completa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación completada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockchainVerificationResponseDTO.class)))
    })
    public ResponseEntity<BlockchainVerificationResponseDTO> verifyBlockchain() {
        boolean valid = blockchainService.verifyBlockchainIntegrity();
        long blockCount = ledgerBlockRepository.count();
        long pendingTransactions = stockLedgerRepository.countByBlockIsNull();
        LedgerBlock latestBlock = ledgerBlockRepository.findTopByOrderByBlockNumberDesc().orElse(null);

        BlockchainVerificationResponseDTO response = BlockchainVerificationResponseDTO.builder()
                .valid(valid)
                .message(valid ? "Blockchain íntegra" : "Blockchain con inconsistencias")
                .blockCount(blockCount)
                .pendingTransactions(pendingTransactions)
                .latestBlockNumber(latestBlock != null ? latestBlock.getBlockNumber() : null)
                .latestBlockHash(latestBlock != null ? latestBlock.getBlockHash() : null)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @Operation(summary = "Obtener estadísticas de la blockchain")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockchainStatsResponseDTO.class)))
    })
    public ResponseEntity<BlockchainStatsResponseDTO> getStats() {
        LedgerBlock latestBlock = ledgerBlockRepository.findTopByOrderByBlockNumberDesc().orElse(null);
        boolean valid = blockchainService.verifyBlockchainIntegrity();

        BlockchainStatsResponseDTO response = BlockchainStatsResponseDTO.builder()
                .blockCount(ledgerBlockRepository.count())
                .pendingTransactions(stockLedgerRepository.countByBlockIsNull())
                .latestBlockNumber(latestBlock != null ? latestBlock.getBlockNumber() : null)
                .latestBlockHash(latestBlock != null ? latestBlock.getBlockHash() : null)
                .valid(valid)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/blocks")
    @Operation(summary = "Listar bloques confirmados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloques obtenidos correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LedgerBlockResponseDTO.class)))
    })
    public ResponseEntity<Page<LedgerBlockResponseDTO>> getBlocks(Pageable pageable) {
        Page<LedgerBlock> blocks = ledgerBlockRepository.findAll(pageable);
        return ResponseEntity.ok(blocks.map(this::toDto));
    }

    @GetMapping("/blocks/{blockNumber}")
    @Operation(summary = "Obtener un bloque concreto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloque obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LedgerBlockResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Bloque no encontrado")
    })
    public ResponseEntity<LedgerBlockResponseDTO> getBlock(@PathVariable Long blockNumber) {
        return ledgerBlockRepository.findByBlockNumber(blockNumber)
                .map(block -> ResponseEntity.ok(toDto(block)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mempool")
    @Operation(summary = "Listar transacciones pendientes en el mempool")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mempool obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockLedgerResponseDTO.class)))
    })
    public ResponseEntity<Page<StockLedgerResponseDTO>> getMempool(Pageable pageable) {
        List<StockLedger> pending = stockLedgerRepository.findPendingTransactionsOrderByIdAsc(pageable);
        long total = stockLedgerRepository.countByBlockIsNull();
        Page<StockLedgerResponseDTO> response = new PageImpl<>(pending.stream()
                .map(stockLedgerMapper::toDTO)
                .toList(), pageable, total);
        return ResponseEntity.ok(response);
    }

    private LedgerBlockResponseDTO toDto(LedgerBlock block) {
        return LedgerBlockResponseDTO.builder()
                .blockNumber(block.getBlockNumber())
                .previousBlockHash(block.getPreviousBlockHash())
                .merkleRoot(block.getMerkleRoot())
                .blockHash(block.getBlockHash())
                .timestamp(block.getTimestamp())
                .transactionCount(block.getTransactionCount())
                .hmacKeyVersion(block.getHmacKeyVersion())
                .build();
    }

    @PostMapping("/sync-stock")
    @Operation(summary = "Sincronizar stock huérfano con el ledger")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock sincronizado exhaustivamente", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string")))
    })
    public ResponseEntity<String> syncStockWithLedger() {
        stockLedgerService.synchronizeStockWithLedger();
        return ResponseEntity.ok("Stock y lotes sincronizados correctamente con el Ledger");
    }
}
