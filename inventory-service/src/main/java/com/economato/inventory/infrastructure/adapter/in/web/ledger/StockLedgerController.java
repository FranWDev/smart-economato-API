package com.economato.inventory.infrastructure.adapter.in.web.ledger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.stock.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.stock.response.BatchStockMovementResponseDTO;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.dto.stock.response.StockSnapshotResponseDTO;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/stock-ledger")
@RequiredArgsConstructor
@Tag(name = "Stock Ledger", description = "Sistema de ledger inmutable con encadenamiento criptográfico")
public class StockLedgerController {

    private final StockLedgerService stockLedgerService;

    @Operation(summary = "Obtener historial de transacciones de un producto", description = "Devuelve todas las transacciones del ledger para un producto específico, ordenadas cronológicamente. Similar a 'git log' para ver el historial completo. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/history/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StockLedgerResponseDTO>> getProductHistory(
            @PathVariable Integer productId,
            Pageable pageable) {
        return ResponseEntity.ok(stockLedgerService.getProductHistoryDto(productId, pageable));
    }

    @Operation(summary = "Verificar integridad de la cadena de un producto", description = "Recalcula todos los hashes de las transacciones de un producto y verifica que coincidan. Si alguien modificó la base de datos directamente, esta verificación lo detectará. Similar a 'git fsck' para verificar la integridad del repositorio. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación completada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IntegrityCheckResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/verify/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IntegrityCheckResponseDTO> verifyProductIntegrity(@PathVariable Integer productId) {
        return ResponseEntity.ok(stockLedgerService.verifyProductIntegrityDto(productId));
    }

    @Operation(summary = "Verificar integridad de TODAS las cadenas", description = "Verifica la integridad de todos los productos del sistema. Esta operación puede tardar varios segundos en sistemas grandes. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación global completada", content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/verify-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<IntegrityCheckResponseDTO>> verifyAllChains() {
        return ResponseEntity.ok(stockLedgerService.verifyAllChainsDto());
    }

    @Operation(summary = "Obtener snapshot de stock actual", description = "Devuelve el estado actual del stock de un producto desde el snapshot optimizado. Esta consulta es O(1) y no requiere recorrer el ledger completo. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockSnapshotResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Snapshot no encontrado")
    })
    @GetMapping("/snapshot/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockSnapshotResponseDTO> getCurrentStock(@PathVariable Integer productId) {
        return ResponseEntity.ok(stockLedgerService.getCurrentStockDto(productId));
    }

    @Operation(summary = "Procesar movimientos de stock en batch (transacción atómica)", description = "Permite actualizar el stock de múltiples productos en una sola transacción. Si algún movimiento falla, se revierten TODOS los cambios (atomicidad). Ideal para rollbacks de recetas u órdenes erróneas. Ejemplo: Si necesitas revertir una receta que usó 3 ingredientes, puedes devolver el stock de los 3 en una sola operación. Si falla uno, ninguno se aplica. [Rol requerido: ADMIN]")
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
        return ResponseEntity.ok(stockLedgerService.processBatchMovementsDto(request));
    }

    @Operation(summary = "Realizar ajuste manual de stock", description = "Permite realizar un ajuste manual de stock para un producto, opcionalmente asignándolo a un lote específico para correcciones de inventario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajuste manual registrado exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockLedgerResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o stock insuficiente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para realizar la operación"),
            @ApiResponse(responseCode = "500", description = "Error en la operación")
    })
    @PostMapping("/manual-adjustment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockLedgerResponseDTO> registerManualAdjustment(
            @Valid @RequestBody ManualStockAdjustmentRequestDTO request) {
        return ResponseEntity.ok(stockLedgerService.registerManualAdjustmentDto(request));
    }

    @Operation(summary = "Obtener consumo de un producto", description = "Calcula el consumo total de un producto en un periodo específico. Se puede solicitar un día específico, los últimos X días o un rango de fechas. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consumo calculado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductConsumptionResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de fecha inválidos"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/consumption/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductConsumptionResponseDTO> getProductConsumption(
            @PathVariable Integer productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer lastDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(stockLedgerService.getProductConsumptionDto(productId, date, lastDays, startDate, endDate));
    }
}
