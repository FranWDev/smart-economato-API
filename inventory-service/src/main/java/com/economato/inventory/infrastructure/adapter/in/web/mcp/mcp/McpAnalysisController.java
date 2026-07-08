package com.economato.inventory.infrastructure.adapter.in.web.mcp.mcp;

import com.economato.inventory.application.dto.mcp.mcp.McpCostBreakdownDto;
import com.economato.inventory.application.dto.mcp.mcp.McpMenuSuggestionDto;
import com.economato.inventory.application.dto.order.mcp.McpReorderSuggestionDto;
import com.economato.inventory.application.dto.stock.mcp.McpStockHealthDto;
import com.economato.inventory.application.dto.mcp.mcp.McpWasteRiskDto;
import com.economato.inventory.application.usecase.mcp.mcp.McpAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/mcp/analysis")
@RequiredArgsConstructor
@Tag(name = "MCP Analysis", description = "Endpoints MCP de analisis compuesto")
public class McpAnalysisController {

    private final McpAnalysisService mcpAnalysisService;

    @GetMapping("/reorder-suggestions")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Sugerencias de reposicion", description = "Calcula productos con deficit proyectado y propone cantidades de pedido con nivel de urgencia.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analisis generado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
        })
    public ResponseEntity<List<McpReorderSuggestionDto>> getReorderSuggestions() {
        return ResponseEntity.ok(mcpAnalysisService.getReorderSuggestions());
    }

    @GetMapping("/waste-risk")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Riesgo de merma", description = "Lista lotes proximos a caducar junto con sugerencias de recetas para reducir desperdicio.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analisis generado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
    public ResponseEntity<List<McpWasteRiskDto>> getWasteRisk() {
        return ResponseEntity.ok(mcpAnalysisService.getWasteRisk());
    }

    @GetMapping("/menu-optimizer")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Optimizador de menu", description = "Propone menu semanal optimizado por presupuesto y exclusiones de alergenos.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sugerencia de menu generada"),
            @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<McpMenuSuggestionDto> getMenuOptimizer(
            @Parameter(description = "Presupuesto total estimado", example = "500") @RequestParam(required = false) BigDecimal budget,
            @Parameter(description = "Alergenos a excluir", example = "gluten,lactosa") @RequestParam(required = false, name = "exclude") List<String> exclude) {
        return ResponseEntity.ok(mcpAnalysisService.getMenuOptimizer(budget, exclude));
    }

    @GetMapping("/cost-breakdown")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Desglose de costes", description = "Calcula coste total y desgloses por producto/receta en un rango temporal.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Desglose generado"),
            @ApiResponse(responseCode = "400", description = "Rango de fechas invalido"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<McpCostBreakdownDto> getCostBreakdown(
            @Parameter(description = "Fecha inicio (yyyy-MM-dd)", example = "2026-03-01") @RequestParam LocalDate from,
            @Parameter(description = "Fecha fin (yyyy-MM-dd)", example = "2026-03-31") @RequestParam LocalDate to) {
        return ResponseEntity.ok(mcpAnalysisService.getCostBreakdown(from, to));
    }

    @GetMapping("/stock-health-score")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Puntuacion de salud de stock", description = "Calcula score global de salud de inventario basado en prediccion, caducidad y alertas.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Puntuacion calculada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
    public ResponseEntity<McpStockHealthDto> getStockHealthScore() {
        return ResponseEntity.ok(mcpAnalysisService.getStockHealthScore());
    }
}
