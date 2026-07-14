package com.economato.inventory.infrastructure.adapter.in.web.blockchain;

import com.economato.inventory.application.dto.blockchain.response.BlockchainStatsResponseDTO;
import com.economato.inventory.application.dto.blockchain.response.BlockchainVerificationResponseDTO;
import com.economato.inventory.application.dto.ledger.response.LedgerBlockResponseDTO;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.usecase.blockchain.BlockchainService;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/blockchain")
@RequiredArgsConstructor
@Profile({"!test", "kafka-test"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Blockchain Admin", description = "Verificación y exploración del ledger blockchain")
public class BlockchainAdminController {

    private final BlockchainService blockchainService;
    private final StockLedgerService stockLedgerService;
    private final I18nService i18nService;

    @GetMapping("/verify")
    @Operation(summary = "Verificar blockchain completa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación completada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockchainVerificationResponseDTO.class)))
    })
    public ResponseEntity<BlockchainVerificationResponseDTO> verifyBlockchain() {
        return ResponseEntity.ok(blockchainService.verifyBlockchain());
    }

    @GetMapping("/stats")
    @Operation(summary = "Obtener estadísticas de la blockchain")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockchainStatsResponseDTO.class)))
    })
    public ResponseEntity<BlockchainStatsResponseDTO> getStats() {
        return ResponseEntity.ok(blockchainService.getStats());
    }

    @GetMapping("/blocks")
    @Operation(summary = "Listar bloques confirmados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloques obtenidos correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LedgerBlockResponseDTO.class)))
    })
    public ResponseEntity<Page<LedgerBlockResponseDTO>> getBlocks(Pageable pageable) {
        return ResponseEntity.ok(blockchainService.getBlocks(pageable));
    }

    @GetMapping("/blocks/{blockNumber}")
    @Operation(summary = "Obtener un bloque concreto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloque obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LedgerBlockResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Bloque no encontrado")
    })
    public ResponseEntity<LedgerBlockResponseDTO> getBlock(@PathVariable Long blockNumber) {
        return blockchainService.getBlock(blockNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mempool")
    @Operation(summary = "Listar transacciones pendientes en el mempool")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mempool obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockLedgerResponseDTO.class)))
    })
    public ResponseEntity<Page<StockLedgerResponseDTO>> getMempool(Pageable pageable) {
        return ResponseEntity.ok(blockchainService.getMempool(pageable));
    }

    @PostMapping("/sync-stock")
    @Operation(summary = "Sincronizar stock huérfano con el ledger")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock sincronizado exhaustivamente", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string")))
    })
    public ResponseEntity<String> syncStockWithLedger() {
        stockLedgerService.synchronizeStockWithLedger();
        return ResponseEntity.ok(i18nService.getMessage(MessageKey.SUCCESS_BLOCKCHAIN_SYNC));
    }

    @PostMapping("/rebuild-all")
    @Operation(summary = "RECONSTRUCCIÓN TOTAL: Recalcula todos los hashes y stocks del ledger desde cero")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Blockchain reconstruida y sincronizada desde cero", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string")))
    })
    public ResponseEntity<String> rebuildAllChains() {
        stockLedgerService.rebuildAllChains();
        return ResponseEntity.ok(i18nService.getMessage(MessageKey.SUCCESS_BLOCKCHAIN_REBUILD));
    }
}
