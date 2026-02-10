package com.economato.inventory.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.service.ProductExcelService;
import com.economato.inventory.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Productos", description = "Operaciones relacionadas con los productos")
public class ProductController {

    private final ProductService productService;
    private final ProductExcelService productExcelService;

    public ProductController(ProductService productService, ProductExcelService productExcelService) {
        this.productService = productService;
        this.productExcelService = productExcelService;
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @Operation(summary = "Obtener todos los productos",
               description = "Devuelve una lista paginada de todos los productos registrados en el sistema. [Rol requerido: USER]")
    @ApiResponse(responseCode = "200", description = "Lista de productos obtenida correctamente",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = ProductResponseDTO.class)))
    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(Pageable pageable) {
        return ResponseEntity.ok(productService.findAll(pageable));
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @Operation(summary = "Descargar productos en Excel",
               description = "Genera y descarga un archivo Excel con todos los productos registrados. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Excel generado correctamente",
                     content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
        @ApiResponse(responseCode = "500", description = "Error al generar el Excel")
    })
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> downloadProductsExcel() {
        try {
            byte[] excelBytes = productExcelService.generateProductsExcel(productService.findAllForExport());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("productos.xlsx")
                    .build());
            headers.setContentLength(excelBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @Operation(summary = "Obtener un producto por código de barras",
               description = "Devuelve la información de un producto específico según su código de barras. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto encontrado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = ProductResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/codebar/{codebar}")
    public ResponseEntity<ProductResponseDTO> getByCodebar(
            @Parameter(description = "Código de barras del producto", example = "95082390574")
            @PathVariable String codebar) {
        return productService.findByCodebar(codebar)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @Operation(summary = "Buscar productos por nombre",
               description = "Devuelve una lista paginada de productos que coincidan con el nombre especificado (búsqueda parcial sin distinción de mayúsculas). [Rol requerido: USER]")
    @ApiResponse(responseCode = "200", description = "Lista de productos encontrados",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = ProductResponseDTO.class)))
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponseDTO>> searchProductsByName(
            @Parameter(description = "Nombre o parte del nombre del producto a buscar", example = "leche")
            @RequestParam String name,
            Pageable pageable) {
        return ResponseEntity.ok(productService.findByName(name, pageable));
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ADMIN')")
    @Operation(summary = "Obtener un producto por ID",
               description = "Devuelve la información de un producto específico según su ID. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto encontrado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = ProductResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(
            @Parameter(description = "ID del producto", example = "3", required = true)
            @PathVariable Integer id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Crear un nuevo producto",
               description = "Registra un nuevo producto en el inventario. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto creado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = ProductResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Datos del producto inválidos")
    })
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductRequestDTO productRequest) {
        return ResponseEntity.ok(productService.save(productRequest));
    }

    @Operation(summary = "Actualizar un producto existente",
               description = "Modifica los datos de un producto existente según su ID. " +
                           "Este endpoint utiliza **bloqueo optimista** con reintentos automáticos " +
                           "para prevenir conflictos de concurrencia. Si múltiples usuarios intentan " +
                           "actualizar el mismo producto simultáneamente, el sistema reintentará hasta 3 veces " +
                           "con un retraso de 100ms entre intentos. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto actualizado correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = ProductResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o producto con nombre duplicado"),
        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - El producto fue modificado por otro usuario. " +
                     "Por favor, recargue los datos e intente nuevamente.")
    })
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @Parameter(description = "ID del producto a actualizar", example = "3", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody ProductRequestDTO productRequest) {
        return productService.update(id, productRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un producto",
               description = "Elimina un producto del inventario según su ID. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Producto eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "ID del producto a eliminar", example = "3", required = true)
            @PathVariable Integer id) {
        return productService.findById(id)
                .map(product -> {
                    productService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Actualizar stock manualmente con auditoría en ledger",
               description = "Modifica los datos de un producto y si el stock cambió, lo registra en el ledger " +
                           "inmutable con el concepto 'Modificación manual'. La acción se audita automáticamente " +
                           "con el usuario autenticado y la orden es nula (por ser modificación manual). " +
                           "Utiliza **bloqueo optimista** con reintentos automáticos. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Producto actualizado correctamente y ledger registrado si el stock cambió",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = ProductResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o producto con nombre duplicado"),
        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - El producto fue modificado por otro usuario")
    })
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PutMapping("/{id}/stock-manual")
    public ResponseEntity<ProductResponseDTO> updateStockManually(
            @Parameter(description = "ID del producto a actualizar", example = "3", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody ProductRequestDTO productRequest) {
        return productService.updateStockManually(id, productRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}