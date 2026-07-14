package com.economato.inventory.infrastructure.adapter.in.web.product;

import com.economato.inventory.application.dto.ledger.response.LedgerPdfResponseDTO;
import com.economato.inventory.application.dto.product.request.ProductRequestDTO;
import com.economato.inventory.application.dto.product.response.ProductResponseDTO;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.product.ProductService;
import com.economato.inventory.infrastructure.adapter.out.external.ledger.reports.StockLedgerPdfService;
import com.economato.inventory.infrastructure.adapter.out.external.product.reports.ProductExcelService;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Operaciones relacionadas con los productos")
public class ProductController {

        private final ProductService productService;
        private final StockLedgerService stockLedgerService;

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Obtener todos los productos", description = "Devuelve una lista paginada de todos los productos registrados en el sistema. [Rol requerido: USER]")
        @ApiResponse(responseCode = "200", description = "Lista de productos obtenida correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class)))
        @GetMapping
        public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(Pageable pageable) {
                return ResponseEntity.ok(productService.findAll(pageable));
        }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Descargar productos en Excel", description = "Genera y descarga un archivo Excel con todos los productos en streaming. [Rol requerido: USER]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Excel generado correctamente", content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                        @ApiResponse(responseCode = "500", description = "Error al generar el Excel")
        })
        @GetMapping("/export/excel")
        public ResponseEntity<StreamingResponseBody> downloadProductsExcel() {
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"productos.xlsx\"")
                                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(productService.getProductsExcelStream());
        }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Obtener un producto por código de barras", description = "Devuelve la información de un producto específico según su código de barras. [Rol requerido: USER]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Producto encontrado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
        })
        @GetMapping("/codebar/{codebar}")
        public ResponseEntity<ProductResponseDTO> getByCodebar(
                        @Parameter(description = "Código de barras del producto", example = "95082390574") @PathVariable String codebar) {
                return productService.findByCodebar(codebar)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Buscar productos por nombre", description = "Devuelve una lista paginada de productos que coincidan con el nombre especificado (búsqueda parcial sin distinción de mayúsculas). [Rol requerido: USER]")
        @ApiResponse(responseCode = "200", description = "Lista de productos encontrados", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class)))
        @GetMapping("/search")
        public ResponseEntity<Page<ProductResponseDTO>> searchProductsByName(
                        @Parameter(description = "Nombre o parte del nombre del producto a buscar", example = "leche") @RequestParam String name,
                        Pageable pageable) {
                return ResponseEntity.ok(productService.findByName(name, pageable));
        }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Obtener un producto por ID", description = "Devuelve la información de un producto específico según su ID. [Rol requerido: USER]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Producto encontrado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
        })
        @GetMapping("/{id}")
        public ResponseEntity<ProductResponseDTO> getProductById(
                        @Parameter(description = "ID del producto", example = "3", required = true) @PathVariable Integer id) {
                return productService.findById(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Crear un nuevo producto", description = "Registra un nuevo producto en el inventario. [Rol requerido: CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Producto creado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Datos del producto inválidos")
        })
        @PostMapping
        public ResponseEntity<ProductResponseDTO> createProduct(
                        @Valid @RequestBody ProductRequestDTO productRequest) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(productService.save(productRequest));
        }

        @Operation(summary = "Actualizar un producto existente", description = "Modifica los datos de un producto existente según su ID. "
                        +
                        "Este endpoint utiliza **bloqueo optimista** con reintentos automáticos " +
                        "para prevenir conflictos de concurrencia. Si múltiples usuarios intentan " +
                        "actualizar el mismo producto simultáneamente, el sistema reintentará hasta 3 veces " +
                        "con un retraso de 100ms entre intentos. [Rol requerido: CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Producto actualizado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos o producto con nombre duplicado"),
                        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - El producto fue modificado por otro usuario. "
                                        +
                                        "Por favor, recargue los datos e intente nuevamente.")
        })
        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @PutMapping("/{id}")
        public ResponseEntity<ProductResponseDTO> updateProduct(
                        @Parameter(description = "ID del producto a actualizar", example = "3", required = true) @PathVariable Integer id,
                        @Valid @RequestBody ProductRequestDTO productRequest) {
                return productService.update(id, productRequest)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasRole('ADMIN')")
        @Deprecated(since = "2026-03", forRemoval = false)
        @Operation(summary = "Eliminar un producto", description = "Elimina un producto del inventario según su ID. [Rol requerido: ADMIN]", deprecated = true)
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Producto eliminado correctamente"),
                        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
        })
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteProduct(
                        @Parameter(description = "ID del producto a eliminar", example = "3", required = true) @PathVariable Integer id) {
                productService.deleteById(id);
                return ResponseEntity.noContent().build();
        }

        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Obtener productos ocultos", description = "Devuelve los productos que están ocultos. Permite filtrar por nombre. [Rol requerido: ADMIN]")
        @ApiResponse(responseCode = "200", description = "Lista de productos encontrados", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class)))
        @GetMapping("/hidden")
        public ResponseEntity<Page<ProductResponseDTO>> getHiddenProducts(
                        @Parameter(description = "Nombre o parte del nombre para filtrar", example = "leche") @RequestParam(required = false) String name,
                        Pageable pageable) {
                return ResponseEntity.ok(productService.findHiddenProducts(name, pageable));
        }

        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Ocultar/Mostrar producto", description = "Cambia el estado de visibilidad de un producto. [Rol requerido: CHEF]")
        @PatchMapping("/{id}/toggle-hidden")
        public ResponseEntity<Void> toggleProductHiddenStatus(
                        @Parameter(description = "ID del producto", example = "3", required = true) @PathVariable Integer id,
                        @Parameter(description = "Estado de visibilidad", required = true) @RequestParam boolean hidden) {
                productService.toggleProductHiddenStatus(id, hidden);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Actualizar stock manualmente con auditoría en ledger", description = "Modifica los datos de un producto y si el stock cambió, lo registra en el ledger "
                        +
                        "inmutable con el concepto 'Modificación manual'. La acción se audita automáticamente " +
                        "con el usuario autenticado y la orden es nula (por ser modificación manual). " +
                        "Utiliza **bloqueo optimista** con reintentos automáticos. [Rol requerido: CHEF]")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Producto actualizado correctamente y ledger registrado si el stock cambió", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponseDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos o producto con nombre duplicado"),
                        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - El producto fue modificado por otro usuario")
        })
        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @PutMapping("/{id}/stock-manual")
        public ResponseEntity<ProductResponseDTO> updateStockManually(
                        @Parameter(description = "ID del producto a actualizar", example = "3", required = true) @PathVariable Integer id,
                        @Valid @RequestBody ProductRequestDTO productRequest) {
                return productService.updateStockManually(id, productRequest)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Obtener productos con historial de ledger", description = "Devuelve una lista paginada de productos que tienen al menos una transacción registrada en el ledger. "
                + "Permite filtrar por nombre con búsqueda parcial (igual que la búsqueda normal de productos). "
                + "Solo devuelve productos con historial, excluyendo productos sin transacciones. [Rol requerido: USER]")
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "Lista paginada de productos con ledger obtenida correctamente")
        })
        @GetMapping("/with-ledger")
        public ResponseEntity<Page<ProductResponseDTO>> getProductsWithLedger(
                        @Parameter(description = "Nombre o parte del nombre para filtrar", example = "leche") @RequestParam(required = false) String name,
                        Pageable pageable) {
                return ResponseEntity.ok(productService.findProductsWithLedger(name, pageable));
        }

        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Verificar integridad de ledgers de productos con historial", description = "Verifica la integridad de las cadenas de hash de todos los productos que tienen transacciones en el ledger. "
                + "Devuelve una lista con el estado de integridad de cada producto, identificando cuáles tienen cadenas corruptas. "
                + "Solo verifica productos con ledger, optimizando el proceso. [Rol requerido: CHEF]")
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "Verificación completada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IntegrityCheckResult.class)))
        })
        @GetMapping("/ledger-integrity")
        public ResponseEntity<List<IntegrityCheckResult>> verifyLedgerIntegrity() {
                List<IntegrityCheckResult> results = stockLedgerService.verifyProductsWithLedger();
                return ResponseEntity.ok(results);
        }

        @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Descargar ledger de stock como PDF firmado con verificación de integridad", description = "Genera y descarga un PDF del historial de stock de un producto con firma criptográfica SHA-256 para uso como prueba forense/legal. "
                + "El PDF incluye autenticidad verificable y verifica automáticamente la integridad de la cadena. "
                + "Si se detecta corrupción, se incluye en los headers HTTP. [Rol requerido: CHEF]")
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "PDF generado correctamente (verifica headers para información de integridad)", content = @Content(mediaType = "application/pdf")),
                @ApiResponse(responseCode = "404", description = "Producto o ledger no encontrado"),
                @ApiResponse(responseCode = "500", description = "Error al generar el PDF")
        })
        @GetMapping("/{id}/ledger/pdf")
        public ResponseEntity<byte[]> downloadStockLedgerPdf(
                @Parameter(description = "ID del producto", required = true) @PathVariable Integer id) {
            return productService.getStockLedgerPdfDownload(id)
                    .map(dto -> {
                        var builder = ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + dto.getFilename())
                                .contentType(MediaType.APPLICATION_PDF)
                                .header("X-Ledger-Integrity-Valid", String.valueOf(dto.isIntegrityValid()))
                                .header("X-Ledger-Integrity-Message", dto.getIntegrityMessage());
                        if (dto.getIntegrityError() != null) {
                            builder.header("X-Ledger-Integrity-Error", dto.getIntegrityError());
                        }
                        return builder.body(dto.getPdfContent());
                    })
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
}