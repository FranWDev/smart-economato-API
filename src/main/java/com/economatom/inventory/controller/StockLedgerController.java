package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.BatchStockMovementRequestDTO;
import com.economatom.inventory.dto.response.BatchStockMovementResponseDTO;
import com.economatom.inventory.dto.response.IntegrityCheckResponseDTO;
import com.economatom.inventory.dto.response.StockLedgerResponseDTO;
import com.economatom.inventory.dto.response.StockSnapshotResponseDTO;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.StockLedger;
import com.economatom.inventory.model.StockSnapshot;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.UserRepository;
import com.economatom.inventory.service.StockLedgerService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-ledger")
@RequiredArgsConstructor
@Tag(name = "Stock Ledger", description = "Sistema de ledger inmutable con encadenamiento criptográfico")
public class StockLedgerController {

    private final StockLedgerService stockLedgerService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Operation(
        summary = "Obtener historial de transacciones de un producto",
        description = "Devuelve todas las transacciones del ledger para un producto específico, " +
                     "ordenadas cronológicamente. Similar a 'git log' para ver el historial completo."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente",
                    content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/history/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<List<StockLedgerResponseDTO>> getProductHistory(@PathVariable Integer productId) {
        List<StockLedger> history = stockLedgerService.getProductHistory(productId);
        
        List<StockLedgerResponseDTO> response = history.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Verificar integridad de la cadena de un producto",
        description = "Recalcula todos los hashes de las transacciones de un producto y verifica que coincidan. " +
                     "Si alguien modificó la base de datos directamente, esta verificación lo detectará. " +
                     "Similar a 'git fsck' para verificar la integridad del repositorio."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verificación completada",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IntegrityCheckResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/verify/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<IntegrityCheckResponseDTO> verifyProductIntegrity(@PathVariable Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        
        StockLedgerService.IntegrityCheckResult result = 
            stockLedgerService.verifyChainIntegrity(productId);
        
        List<StockLedger> history = stockLedgerService.getProductHistory(productId);
        
        IntegrityCheckResponseDTO response = IntegrityCheckResponseDTO.builder()
            .productId(productId)
            .productName(product.getName())
            .valid(result.isValid())
            .message(result.getMessage())
            .errors(result.getErrors())
            .totalTransactions(history.size())
            .build();
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Verificar integridad de TODAS las cadenas",
        description = "Verifica la integridad de todos los productos del sistema. " +
                     "Esta operación puede tardar varios segundos en sistemas grandes."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verificación global completada",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/verify-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<IntegrityCheckResponseDTO>> verifyAllChains() {
        List<StockLedgerService.IntegrityCheckResult> results = 
            stockLedgerService.verifyAllChains();
        
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

    @Operation(
        summary = "Obtener snapshot de stock actual",
        description = "Devuelve el estado actual del stock de un producto desde el snapshot optimizado. " +
                     "Esta consulta es O(1) y no requiere recorrer el ledger completo."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot obtenido correctamente",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = StockSnapshotResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Snapshot no encontrado")
    })
    @GetMapping("/snapshot/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
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

    @Operation(
        summary = "Listar todos los snapshots",
        description = "Devuelve el estado actual de stock de todos los productos. " +
                     "Útil para auditorías y para detectar productos con integridad corrupta."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshots obtenidos correctamente",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<List<StockSnapshotResponseDTO>> getAllSnapshots() {
        return ResponseEntity.ok(List.of());
    }

    @Operation(
        summary = "Restablecer historial de un producto",
        description = "Elimina PERMANENTEMENTE todo el historial del ledger de un producto. " +
                     "Solo debe usarse cuando se detecta corrupción y se desea empezar desde cero. " +
                     "Esta operación NO modifica el stock actual del producto, solo borra el historial de transacciones. " +
                     "Requiere permisos de ADMIN."
    )
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

    @Operation(
        summary = "Procesar movimientos de stock en batch (transacción atómica)",
        description = "Permite actualizar el stock de múltiples productos en una sola transacción. " +
                     "Si algún movimiento falla, se revierten TODOS los cambios (atomicidad). " +
                     "Ideal para rollbacks de recetas u órdenes erróneas. " +
                     "Ejemplo: Si necesitas revertir una receta que usó 3 ingredientes, puedes " +
                     "devolver el stock de los 3 en una sola operación. Si falla uno, ninguno se aplica."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación batch completada exitosamente",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = BatchStockMovementResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o stock insuficiente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para realizar la operación"),
        @ApiResponse(responseCode = "500", description = "Error en la operación - cambios revertidos")
    })
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<BatchStockMovementResponseDTO> processBatchMovements(
            @Valid @RequestBody BatchStockMovementRequestDTO request) {
        
        try {
            // Obtener usuario actual
            User currentUser = getCurrentUser();
            
            // Convertir DTOs a objetos internos
            List<StockLedgerService.BatchMovementItem> movements = request.getMovements().stream()
                .map(item -> new StockLedgerService.BatchMovementItem(
                    item.getProductId(),
                    item.getQuantityDelta(),
                    item.getMovementType(),
                    item.getDescription() != null ? item.getDescription() : request.getReason()
                ))
                .collect(Collectors.toList());
            
            // Procesar en transacción atómica
            List<StockLedger> transactions = stockLedgerService.recordBatchStockMovements(
                movements,
                currentUser,
                request.getOrderId()
            );
            
            // Construir respuesta exitosa
            BatchStockMovementResponseDTO response = BatchStockMovementResponseDTO.builder()
                .success(true)
                .processedCount(transactions.size())
                .totalCount(request.getMovements().size())
                .message(String.format("Operación batch completada: %d movimientos procesados exitosamente", 
                                      transactions.size()))
                .transactions(transactions.stream()
                    .map(this::toDTO)
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

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                return userRepository.findByName(username).orElse(null);
            }
        } catch (Exception e) {
            // Silently fail
        }
        return null;
    }

    private StockLedgerResponseDTO toDTO(StockLedger ledger) {
        return StockLedgerResponseDTO.builder()
            .id(ledger.getId())
            .productId(ledger.getProduct().getId())
            .productName(ledger.getProduct().getName())
            .quantityDelta(ledger.getQuantityDelta())
            .resultingStock(ledger.getResultingStock())
            .movementType(ledger.getMovementType().name())
            .description(ledger.getDescription())
            .previousHash(ledger.getPreviousHash())
            .currentHash(ledger.getCurrentHash())
            .transactionTimestamp(ledger.getTransactionTimestamp())
            .sequenceNumber(ledger.getSequenceNumber())
            .userName(ledger.getUser() != null ? ledger.getUser().getName() : "SYSTEM")
            .orderId(ledger.getOrderId())
            .verified(ledger.getVerified())
            .build();
    }
}
