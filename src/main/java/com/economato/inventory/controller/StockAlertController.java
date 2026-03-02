package com.economato.inventory.controller;

import com.economato.inventory.dto.response.AlertSeverity;
import com.economato.inventory.dto.response.StockAlertDTO;
import com.economato.inventory.dto.response.StockPredictionResponseDTO;
import com.economato.inventory.service.StockAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-alerts")
@Tag(name = "Alertas de Stock", description = "Alertas predictivas de stock bajo basadas en historial de cocinado (Holt-Winters). [Rol requerido: CHEF]")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasRole('CHEF')")
    @GetMapping
    @Operation(summary = "Obtener alertas de stock bajo", description = """
            Devuelve las alertas predictivas de stock bajo para todos los ingredientes
            con historial de cocinado. Cada alerta incluye:
            - Consumo proyectado para los próximos 14 días (modelo Holt-Winters)
            - Stock actual y cantidades en pedidos activos (CREATED / PENDING / REVIEW)
            - Nivel de severidad (LOW / MEDIUM / HIGH / CRITICAL)
            - Resolución (COVERED_BY_ORDER / PARTIALLY_COVERED / UNCOVERED)
            - Mensaje localizado con el resumen de la situación
            - Las 3 recetas que más consumen ese ingrediente

            [Rol requerido: CHEF]
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alertas generadas correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockAlertDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<List<StockAlertDTO>> getAlerts(
            @Parameter(description = "Filtrar por severidad mínima (LOW, MEDIUM, HIGH, CRITICAL). Si no se especifica, devuelve todas las alertas activas.") @RequestParam(required = false) AlertSeverity severity) {

        List<StockAlertDTO> alerts = (severity != null)
                ? stockAlertService.getAlertsBySeverity(severity)
                : stockAlertService.getActiveAlerts();

        return ResponseEntity.ok(alerts);
    }

    @PreAuthorize("hasRole('CHEF')")
    @GetMapping("/{productId}")
    @Operation(summary = "Obtener alerta de un producto específico", description = "Calcula la alerta predictiva para un producto individual. [Rol requerido: CHEF]")
    public ResponseEntity<StockAlertDTO> getProductAlert(@PathVariable Integer productId) {
        return stockAlertService.getAlertByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasRole('CHEF')")
    @PostMapping("/batch")
    @Operation(summary = "Obtener alertas para una lista de productos", description = "Calcula las alertas predictivas para un conjunto de IDs de producto. [Rol requerido: CHEF]")
    public ResponseEntity<List<StockAlertDTO>> getBatchAlerts(@RequestBody List<Integer> productIds) {
        return ResponseEntity.ok(stockAlertService.getAlertsByProductIds(productIds));
    }

    @PreAuthorize("hasRole('CHEF')")
    @GetMapping("/predictions")
    @Operation(summary = "Obtener todas las predicciones persistidas", description = "Devuelve una lista paginada de los consumos proyectados para los próximos 14 días. [Rol requerido: CHEF]")
    public ResponseEntity<Page<StockPredictionResponseDTO>> getPredictions(Pageable pageable) {
        return ResponseEntity.ok(stockAlertService.getAllPredictions(pageable));
    }
}
