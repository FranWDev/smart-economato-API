package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.application.dto.response.ProductBatchResponseDTO;
import com.economato.inventory.application.mapper.ProductBatchMapper;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/batches")
@RequiredArgsConstructor
@Tag(name = "Lotes de Productos", description = "Operaciones relacionadas con los lotes y caducidades")
public class ProductBatchController {

    private final ProductBatchService productBatchService;
    private final ProductBatchMapper productBatchMapper;
    private final StockLedgerService stockLedgerService;

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtener lotes próximos a caducar")
    @GetMapping("/expiring")
    public ResponseEntity<List<ProductBatchResponseDTO>> getExpiringBatches(
            @Parameter(description = "Días para considerar próximos a caducar", example = "7")
            @RequestParam(defaultValue = "7") int days) {
        List<ProductBatchResponseDTO> response = productBatchService.getExpiringBatches(days).stream()
                .map(productBatchMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtener lotes caducados con stock restante")
    @GetMapping("/expired")
    public ResponseEntity<List<ProductBatchResponseDTO>> getExpiredBatches() {
        List<ProductBatchResponseDTO> response = productBatchService.getExpiredBatches().stream()
                .map(productBatchMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtener lotes activos de un producto")
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductBatchResponseDTO>> getActiveBatches(
            @Parameter(description = "ID del producto", required = true) @PathVariable Integer productId) {
        List<ProductBatchResponseDTO> response = productBatchService.getActiveBatches(productId).stream()
                .map(productBatchMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Retirar lote caducado", description = "Retira el stock restante de un lote caducado y lo registra como merma.")
    @PostMapping("/{batchId}/withdraw")
    public ResponseEntity<Void> withdrawBatch(@PathVariable Long batchId) {
        stockLedgerService.withdrawExpiredBatch(batchId);
        return ResponseEntity.noContent().build();
    }
}
