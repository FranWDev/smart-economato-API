package com.economato.inventory.infrastructure.adapter.in.web.mcp.mcp;
import com.economato.inventory.application.dto.mcp.mcp.McpBulkRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpSearchResultDto;
import com.economato.inventory.application.dto.mcp.mcp.McpSystemContextDto;
import com.economato.inventory.application.dto.order.mcp.McpOrderDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;


import com.economato.inventory.application.usecase.mcp.mcp.McpSearchService;
import com.economato.inventory.application.usecase.mcp.mcp.McpUtilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Utilities", description = "Endpoints optimizados para consumo por IAs (Model Context Protocol)")
public class McpUtilityController {

    private final McpUtilityService mcpUtilityService;
    private final McpSearchService mcpSearchService;

    public McpUtilityController(McpUtilityService mcpUtilityService, 
                                McpSearchService mcpSearchService) {
        this.mcpUtilityService = mcpUtilityService;
        this.mcpSearchService = mcpSearchService;
    }

    @GetMapping("/context")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtener contexto global", description = "Devuelve un resumen del estado del sistema.")
    public ResponseEntity<McpSystemContextDto> getSystemContext() {
        return ResponseEntity.ok(mcpUtilityService.getSystemContext());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Búsqueda unificada", description = "Busca productos y recetas en una sola llamada.")
    public ResponseEntity<McpSearchResultDto> unifiedSearch(@RequestParam String q) {
        return ResponseEntity.ok(mcpSearchService.unifiedSearch(q));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Listado de productos con filtros", description = "Permite filtrar productos por precio.")
    public ResponseEntity<List<McpProductDto>> getProducts(
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return ResponseEntity.ok(mcpUtilityService.getProductsWithFilters(minPrice, maxPrice, null));
    }


    @GetMapping("/orders/pending")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Pedidos pendientes", description = "Obtiene los pedidos que están en estado PENDING o ORDERED.")
    public ResponseEntity<List<McpOrderDto>> getPendingOrders() {
        return ResponseEntity.ok(mcpUtilityService.getPendingOrders());
    }

    @PostMapping("/bulk/products")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtención masiva de productos", description = "Recupera múltiples productos mediante una lista de IDs o códigos.")
    public ResponseEntity<List<McpProductDto>> getProductsBulk(@RequestBody McpBulkRequest request) {
        return ResponseEntity.ok(mcpUtilityService.getProductsBulk(request));
    }

    @PostMapping("/bulk/orders")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtención masiva de pedidos", description = "Recupera múltiples pedidos mediante una lista de IDs.")
    public ResponseEntity<List<McpOrderDto>> getOrdersBulk(@RequestBody List<Integer> ids) {
        return ResponseEntity.ok(mcpUtilityService.getOrdersBulk(ids));
    }

    @PostMapping("/bulk/recipes")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Obtención masiva de recetas", description = "Recupera múltiples recetas mediante una lista de IDs.")
    public ResponseEntity<List<McpRecipeDto>> getRecipesBulk(@RequestBody List<Integer> ids) {
        return ResponseEntity.ok(mcpUtilityService.getRecipesBulk(ids));
    }
}
