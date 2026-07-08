package com.economato.inventory.infrastructure.adapter.in.web.stock;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.economato.inventory.application.dto.stock.response.AlertSeverity;
import com.economato.inventory.application.dto.stock.response.DailyForecastResponseDTO;
import com.economato.inventory.application.dto.stock.response.StockAlertDTO;
import com.economato.inventory.application.dto.stock.response.StockPredictionResponseDTO;
import com.economato.inventory.application.dto.shared.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.application.usecase.stock.StockAlertService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/stock-alerts")
@Tag(name = "Alertas de Stock", description = "Alertas predictivas de stock bajo basadas en predicciones de IA persistidas desde el predictor Kafka (Meta Prophet). [Rol requerido: CHEF]")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener alertas de stock bajo", description = """
            Devuelve las alertas predictivas de stock bajo para todos los ingredientes
            con historial de cocinado. Cada alerta incluye:
            - Consumo proyectado para los próximos 14 días calculado por el predictor de IA
            - Stock actual y cantidades en pedidos activos (CREATED / PENDING / REVIEW)
            - Nivel de severidad (LOW / MEDIUM / HIGH / CRITICAL)
            - Resolución (COVERED_BY_ORDER / PARTIALLY_COVERED / UNCOVERED)
            - Mensaje localizado con el resumen de la situación
            - Las 3 recetas que más consumen ese ingrediente

            [Rol requerido: CHEF o ADMIN]
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

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/{productId}")
    @Operation(summary = "Obtener alerta de un producto específico", description = "Calcula la alerta predictiva para un producto individual. [Rol requerido: CHEF o ADMIN]")
    public ResponseEntity<StockAlertDTO> getProductAlert(@PathVariable Integer productId) {
        return stockAlertService.getAlertByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @PostMapping("/batch")
    @Operation(summary = "Obtener alertas para una lista de productos", description = "Calcula las alertas predictivas para un conjunto de IDs de producto. [Rol requerido: CHEF o ADMIN]")
    public ResponseEntity<List<StockAlertDTO>> getBatchAlerts(@RequestBody List<Integer> productIds) {
        return ResponseEntity.ok(stockAlertService.getAlertsByProductIds(productIds));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/predictions")
    @Operation(summary = "Obtener todas las predicciones persistidas", description = "Devuelve una lista paginada de los consumos proyectados para los próximos 14 días calculados por el predictor de IA (Meta Prophet). [Rol requerido: CHEF o ADMIN]")
    public ResponseEntity<Page<StockPredictionResponseDTO>> getPredictions(Pageable pageable) {
        return ResponseEntity.ok(stockAlertService.getAllPredictions(pageable));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/history")
    @Operation(summary = "Obtener historial semanal de consumo", description = "Devuelve una lista paginada del historial semanal de consumo (últimas 12 semanas) para todos los productos con historial. [Rol requerido: CHEF o ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial semanal obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyConsumptionResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<Page<WeeklyConsumptionResponseDTO>> getWeeklyConsumptionHistoryAll(Pageable pageable) {
        return ResponseEntity.ok(stockAlertService.getWeeklyConsumptionHistoryAll(pageable));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @GetMapping("/history/{productId}")
    @Operation(summary = "Obtener historial semanal de un producto", description = "Devuelve el historial semanal de consumo (últimas 12 semanas) para un producto específico. [Rol requerido: CHEF o ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial semanal del producto obtenido correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyConsumptionResponseDTO.class))),
            @ApiResponse(responseCode = "204", description = "No hay datos históricos para el producto"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<WeeklyConsumptionResponseDTO> getWeeklyConsumptionHistoryByProduct(@PathVariable Integer productId) {
        List<WeeklyConsumptionResponseDTO> history = stockAlertService.getWeeklyConsumptionHistory(productId);
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history.get(0));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Deprecated
    @GetMapping("/forecast")
    @Operation(summary = "[LEGACY] Obtener proyección diaria de consumo", description = "Endpoint legado basado en Holt-Winters. Está deprecado y ya no alimenta las alertas oficiales de stock, que usan exclusivamente predicciones de IA. [Rol requerido: CHEF o ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyección diaria obtenida correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DailyForecastResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<Page<DailyForecastResponseDTO>> getDailyForecastAll(Pageable pageable) {
        return ResponseEntity.ok(stockAlertService.getDailyForecastAll(pageable));
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Deprecated
    @GetMapping("/forecast/{productId}")
    @Operation(summary = "[LEGACY] Obtener proyección diaria de un producto", description = "Endpoint legado basado en Holt-Winters. Está deprecado y ya no alimenta las alertas oficiales de stock, que usan exclusivamente predicciones de IA. [Rol requerido: CHEF o ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyección diaria del producto obtenida correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DailyForecastResponseDTO.class))),
            @ApiResponse(responseCode = "204", description = "No hay datos históricos para el producto"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<DailyForecastResponseDTO> getDailyForecastByProduct(@PathVariable Integer productId) {
        return stockAlertService.getDailyForecast(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
