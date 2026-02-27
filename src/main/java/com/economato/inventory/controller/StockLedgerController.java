package com.economato.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.request.BatchStockMovementRequestDTO;
import com.economato.inventory.dto.response.IntegrityCheckResult;
import com.economato.inventory.dto.response.BatchStockMovementResponseDTO;
import com.economato.inventory.dto.response.IntegrityCheckResponseDTO;
import com.economato.inventory.dto.response.StockLedgerResponseDTO;
import com.economato.inventory.dto.response.StockSnapshotResponseDTO;
import com.economato.inventory.mapper.StockLedgerMapper;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.StockSnapshot;
import com.economato.inventory.service.StockLedgerService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-ledger")
@RequiredArgsConstructor
@Tag(name = "Stock Ledger", description = "Sistema de ledger inmutable con encadenamiento criptográfico")
public class StockLedgerController {

    private final StockLedgerService stockLedgerService;
    private final StockLedgerMapper stockLedgerMapper;

    @Operation(summary = "Obtener historial de transacciones de un producto", description = "Devuelve todas las transacciones del ledger para un producto específico, "
            +
            "ordenadas cronológicamente. Similar a 'git log' para ver el historial completo. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/history/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockLedgerResponseDTO>> getProductHistory(@PathVariable Integer productId) {
        List<StockLedger> history = stockLedgerService.getProductHistory(productId);

        List<StockLedgerResponseDTO> response = history.stream()
                .map(stockLedgerMapper::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verificar integridad de la cadena de un producto", description = "Recalcula todos los hashes de las transacciones de un producto y verifica que coincidan. "
            +
            "Si alguien modificó la base de datos directamente, esta verificación lo detectará. " +
            "Similar a 'git fsck' para verificar la integridad del repositorio. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación completada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IntegrityCheckResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/verify/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IntegrityCheckResponseDTO> verifyProductIntegrity(@PathVariable Integer productId) {
        IntegrityCheckResult result = stockLedgerService.verifyChainIntegrity(productId);

        List<StockLedger> history = stockLedgerService.getProductHistory(productId);

        IntegrityCheckResponseDTO response = IntegrityCheckResponseDTO.builder()
                .productId(result.getProductId())
                .productName(result.getProductName())
                .valid(result.isValid())
                .message(result.getMessage())
                .errors(result.getErrors())
                .totalTransactions(history.size())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verificar integridad de TODAS las cadenas", description = "Verifica la integridad de todos los productos del sistema. "
            +
            "Esta operación puede tardar varios segundos en sistemas grandes. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación global completada", content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/verify-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<IntegrityCheckResponseDTO>> verifyAllChains() {
        List<IntegrityCheckResult> results = stockLedgerService.verifyAllChains();

        List<IntegrityCheckResponseDTO> response = results.stream()
                .map(result -> {
                    return IntegrityCheckResponseDTO.builder()
                            .valid(result.isValid())
                            .message(result.getMessage())
                            .errors(result.getErrors())
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obtener snapshot de stock actual", description = "Devuelve el estado actual del stock de un producto desde el snapshot optimizado. "
            +
            "Esta consulta es O(1) y no requiere recorrer el ledger completo. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockSnapshotResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Snapshot no encontrado")
    })
    @GetMapping("/snapshot/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockSnapshotResponseDTO> getCurrentStock(@PathVariable Integer productId) {
        StockSnapshot snapshot = stockLedgerService.getCurrentStock(productId)
                .orElseThrow(() -> new RuntimeException("Snapshot no encontrado para el producto " + productId));

        StockSnapshotResponseDTO response = StockSnapshotResponseDTO.builder()
                .productId(snapshot.getProductId())
                .productName(snapshot.getProduct().getName())
                .currentStock(snapshot.getCurrentStock())
                .lastTransactionHash(snapshot.getLastTransactionHash())
                .lastSequenceNumber(snapshot.getLastSequenceNumber())
                .lastUpdated(snapshot.getLastUpdated())
                .lastVerified(snapshot.getLastVerified())
                .integrityStatus(snapshot.getIntegrityStatus())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Restablecer historial de un producto", description = "Elimina PERMANENTEMENTE todo el historial del ledger de un producto. "
            +
            "Solo debe usarse cuando se detecta corrupción y se desea empezar desde cero. " +
            "Esta operación NO modifica el stock actual del producto, solo borra el historial de transacciones. " +
            "Requiere permisos de ADMIN. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial restablecido correctamente"),
            @ApiResponse(responseCode = "403", description = "Requiere permisos de ADMIN"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @DeleteMapping("/reset/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> resetProductLedger(@PathVariable Integer productId) {
        String message = stockLedgerService.resetProductLedger(productId);
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "Procesar movimientos de stock en batch (transacción atómica)", description = "Permite actualizar el stock de múltiples productos en una sola transacción. "
            +
            "Si algún movimiento falla, se revierten TODOS los cambios (atomicidad). " +
            "Ideal para rollbacks de recetas u órdenes erróneas. " +
            "Ejemplo: Si necesitas revertir una receta que usó 3 ingredientes, puedes " +
            "devolver el stock de los 3 en una sola operación. Si falla uno, ninguno se aplica. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operación batch completada exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchStockMovementResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o stock insuficiente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para realizar la operación"),
            @ApiResponse(responseCode = "500", description = "Error en la operación - cambios revertidos")
    })
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchStockMovementResponseDTO> processBatchMovements(
            @Valid @RequestBody BatchStockMovementRequestDTO request) {

        try {
            // Procesar en transacción atómica (el servicio maneja la conversión y el
            // usuario)
            List<StockLedger> transactions = stockLedgerService.processBatchMovements(request);

            // Construir respuesta exitosa
            BatchStockMovementResponseDTO response = BatchStockMovementResponseDTO.builder()
                    .success(true)
                    .processedCount(transactions.size())
                    .totalCount(request.getMovements().size())
                    .message(String.format("Operación batch completada: %d movimientos procesados exitosamente",
                            transactions.size()))
                    .transactions(transactions.stream()
                            .map(stockLedgerMapper::toDTO)
                            .collect(Collectors.toList()))
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // Error - la transacción se ha revertido automáticamente
            BatchStockMovementResponseDTO errorResponse = BatchStockMovementResponseDTO.builder()
                    .success(false)
                    .processedCount(0)
                    .totalCount(request.getMovements().size())
                    .message("Error en operación batch - todos los cambios han sido revertidos")
                    .errorDetail(e.getMessage())
                    .build();

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

}
