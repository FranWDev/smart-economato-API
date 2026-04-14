package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.dto.mcp.*;
import io.swagger.v3.oas.annotations.Parameter;
import com.economato.inventory.application.usecase.mcp.McpToolReadService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP Tool Read", description = "Endpoints MCP de lectura profunda")
public class McpToolReadController {

    private final McpToolReadService mcpToolReadService;

    @GetMapping("/products/{id}/deep")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Producto en detalle", description = "Devuelve un producto con informacion ampliada: proveedor, prediccion, forecast diario, consumo semanal y lotes FEFO.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
        })
        public ResponseEntity<McpProductDeepDto> getProductDeep(@Parameter(description = "ID del producto", example = "42") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getProductDeep(id));
    }

    @GetMapping("/products/{id}/batches")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Lotes activos de producto", description = "Lista lotes activos del producto ordenados por caducidad para estrategia FEFO.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lotes recuperados"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<McpBatchDto>> getProductBatches(@Parameter(description = "ID del producto", example = "42") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getProductBatches(id));
    }

    @GetMapping("/products/{id}/forecast")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Forecast diario de consumo", description = "Obtiene la serie de pronostico diario del producto para soporte de decisiones de reposicion.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Forecast recuperado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<BigDecimal>> getProductForecast(@Parameter(description = "ID del producto", example = "42") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getProductForecast(id));
    }

    @GetMapping("/products/{id}/consumption-history")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Historico semanal de consumo", description = "Recupera el historico agregado por semanas del consumo del producto.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historico recuperado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<BigDecimal>> getProductConsumptionHistory(@Parameter(description = "ID del producto", example = "42") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getProductConsumptionHistory(id));
    }

    @GetMapping("/products/{id}/ledger")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Movimientos de ledger por producto", description = "Devuelve los ultimos movimientos de stock ledger para trazabilidad operativa del producto.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Movimientos recuperados"),
            @ApiResponse(responseCode = "400", description = "Parametro limit invalido"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<McpLedgerEntryDto>> getProductLedger(
            @Parameter(description = "ID del producto", example = "42") @PathVariable Integer id,
            @Parameter(description = "Numero maximo de movimientos a devolver (1-200)", example = "20") @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(mcpToolReadService.getProductLedger(id, limit));
    }

    @GetMapping("/recipes/{id}/deep")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Receta en detalle", description = "Devuelve receta con componentes, alergenos, coste por porcion y frecuencia reciente de cocinado.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receta encontrada"),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<McpRecipeDeepDto> getRecipeDeep(@Parameter(description = "ID de la receta", example = "15") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getRecipeDeep(id));
    }

    @GetMapping("/recipes/{id}/feasibility")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Viabilidad de receta", description = "Calcula si una receta es viable para un numero de porciones segun stock y disponibilidad actuales.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analisis de viabilidad generado"),
            @ApiResponse(responseCode = "400", description = "Parametro portions invalido"),
            @ApiResponse(responseCode = "404", description = "Receta no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<McpFeasibilityDto> checkFeasibility(
            @Parameter(description = "ID de la receta", example = "15") @PathVariable Integer id,
            @Parameter(description = "Numero de porciones a evaluar", example = "10") @RequestParam BigDecimal portions) {
        return ResponseEntity.ok(mcpToolReadService.checkFeasibility(id, portions));
    }

    @GetMapping("/recipes/by-allergen-exclusion")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Recetas por exclusion de alergenos", description = "Lista recetas que no contienen los alergenos indicados.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recetas filtradas"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<McpRecipeDto>> getRecipesByAllergenExclusion(
            @Parameter(description = "Lista de alergenos a excluir", example = "gluten,lactosa") @RequestParam(name = "exclude") List<String> exclude) {
        return ResponseEntity.ok(mcpToolReadService.getRecipesByAllergenExclusion(exclude));
    }

    @GetMapping("/weekly-plan/current/deep")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Plan semanal actual ampliado", description = "Obtiene el plan semanal vigente con slots y estado operativo para consumo MCP.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan semanal recuperado"),
            @ApiResponse(responseCode = "404", description = "No existe plan semanal activo"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
    public ResponseEntity<McpWeeklyPlanDeepDto> getCurrentWeeklyPlanDeep() {
        return ResponseEntity.ok(mcpToolReadService.getCurrentWeeklyPlanDeep());
    }

    @GetMapping("/suppliers/{id}/deep")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Proveedor en detalle", description = "Devuelve proveedor con productos asociados, actividad reciente y estado de crisis activa.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Proveedor encontrado"),
            @ApiResponse(responseCode = "404", description = "Proveedor no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<McpSupplierDeepDto> getSupplierDeep(@Parameter(description = "ID del proveedor", example = "3") @PathVariable Integer id) {
        return ResponseEntity.ok(mcpToolReadService.getSupplierDeep(id));
    }

    @GetMapping("/crises/active")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Crisis activas", description = "Recupera las crisis alimentarias activas con proveedor y productos afectados.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Crisis activas recuperadas"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
    public ResponseEntity<List<McpCrisisDto>> getActiveCrises() {
        return ResponseEntity.ok(mcpToolReadService.getActiveCrises());
    }

    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Lotes proximos a caducar", description = "Lista lotes con caducidad en los proximos N dias para prevenir merma.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lotes recuperados"),
            @ApiResponse(responseCode = "400", description = "Parametro days invalido"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
        public ResponseEntity<List<McpExpiringBatchDto>> getExpiringSoon(@Parameter(description = "Horizonte en dias", example = "7") @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(mcpToolReadService.getExpiringSoon(days));
    }

    @GetMapping("/alerts/active")
    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
        @Operation(summary = "Alertas activas de stock", description = "Devuelve alertas activas de riesgo de stock para priorizar accion operativa.")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alertas recuperadas"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para consultar")
        })
    public ResponseEntity<List<McpAlertDto>> getActiveAlerts() {
        return ResponseEntity.ok(mcpToolReadService.getActiveAlerts());
    }
}
