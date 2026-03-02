package com.economato.inventory.controller;

import com.economato.inventory.dto.response.AlertSeverity;
import com.economato.inventory.dto.response.StockAlertDTO;
import com.economato.inventory.service.StockAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock-alerts")
@Tag(name = "Alertas de Stock", description = "Alertas predictivas de stock bajo basadas en historial de cocinado (Holt-Winters). [Rol requerido: ADMIN]")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener alertas de stock bajo", description = """
            Devuelve las alertas predictivas de stock bajo para todos los ingredientes
            con historial de cocinado. Cada alerta incluye:
            - Consumo proyectado para los próximos 14 días (modelo Holt-Winters)
            - Stock actual y cantidades en pedidos activos (CREATED / PENDING / REVIEW)
            - Nivel de severidad (LOW / MEDIUM / HIGH / CRITICAL)
            - Resolución (COVERED_BY_ORDER / PARTIALLY_COVERED / UNCOVERED)
            - Mensaje en español con el resumen de la situación
            - Las 3 recetas que más consumen ese ingrediente

            [Rol requerido: ADMIN]
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
}
