package com.economato.inventory.infrastructure.adapter.in.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.application.dto.request.UpdateBatchExpirationRequestDTO;
import com.economato.inventory.application.dto.response.ProductBatchResponseDTO;
import com.economato.inventory.application.mapper.ProductBatchMapper;
import com.economato.inventory.application.usecase.ProductBatchService;
import com.economato.inventory.application.usecase.StockLedgerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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

    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Actualizar fecha de caducidad de un lote",
               description = "Permite corregir o actualizar la fecha de caducidad de un lote activo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caducidad actualizada correctamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o lote agotado"),
        @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    @PatchMapping("/{batchId}/expiration")
    public ResponseEntity<ProductBatchResponseDTO> updateBatchExpiration(
            @PathVariable Long batchId,
            @Valid @RequestBody UpdateBatchExpirationRequestDTO request) {
        var updated = productBatchService.updateExpirationDate(
            batchId, request.getExpirationDate(), request.getReason(), request.getBatchCode());
        return ResponseEntity.ok(productBatchMapper.toResponseDTO(updated));
    }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Buscar lote por código de lote")
        @GetMapping("/by-code/{batchCode}")
        public ResponseEntity<ProductBatchResponseDTO> getBatchByCode(
            @Parameter(description = "Código de lote", required = true) @PathVariable String batchCode) {
        var batch = productBatchService.findByBatchCode(batchCode)
            .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado con código: " + batchCode));
        return ResponseEntity.ok(productBatchMapper.toResponseDTO(batch));
        }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Listar lotes paginados y filtrados")
    @GetMapping
    public ResponseEntity<Page<ProductBatchResponseDTO>> getAllBatches(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean depleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "expirationDate,asc") String sort) {
        
        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        Sort.Direction sortDirection = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ProductBatchResponseDTO> response = productBatchService.findAllBatches(search, depleted, pageable)
                .map(productBatchMapper::toResponseDTO);
                
        return ResponseEntity.ok(response);
    }
}
