package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.dto.mcp.*;
import com.economato.inventory.application.usecase.mcp.McpToolWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP Tool Write", description = "Endpoints MCP de escritura")
public class McpToolWriteController {

    private final McpToolWriteService mcpToolWriteService;

    @PostMapping("/tools/create-order")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Crear pedido", description = "Crea un pedido desde MCP delegando en la logica de negocio de pedidos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<McpOrderDto> createOrder(@RequestBody(description = "Datos de creacion de pedido", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpCreateOrderRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.createOrder(request));
    }

    @PostMapping("/tools/cook-recipe")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Cocinar receta", description = "Ejecuta el cocinado de una receta y descuenta stock en ledger.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receta cocinada correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida o stock insuficiente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<McpRecipeDto> cookRecipe(@RequestBody(description = "Datos de cocinado", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpCookRecipeRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.cookRecipe(request));
    }

    @PostMapping("/tools/adjust-stock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajustar stock", description = "Registra un ajuste manual de stock para un producto.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ajuste registrado"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<Map<String, Object>> adjustStock(@RequestBody(description = "Datos de ajuste manual", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpAdjustStockRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.adjustStock(request));
    }

    @PostMapping("/tools/plan-slot")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Planificar slot", description = "Crea o programa un slot en el plan semanal.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Slot planificado"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<McpSlotDto> planSlot(@RequestBody(description = "Datos de planificacion de slot", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpPlanSlotRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.planSlot(request));
    }

    @PostMapping("/tools/confirm-slot/{planId}/{slotId}")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Confirmar slot", description = "Confirma un slot planificado dentro de un plan semanal.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Slot confirmado"),
            @ApiResponse(responseCode = "404", description = "Plan o slot no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<McpSlotDto> confirmSlot(
            @Parameter(description = "ID del plan semanal", example = "12") @PathVariable Long planId,
            @Parameter(description = "ID del slot", example = "44") @PathVariable Long slotId) {
        return ResponseEntity.ok(mcpToolWriteService.confirmSlot(planId, slotId));
    }

    @PostMapping("/tools/quarantine-batch")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Solicitar cuarentena de lote", description = "Registra solicitud operativa de cuarentena para un lote.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Solicitud de cuarentena registrada"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<Map<String, Object>> quarantineBatch(@RequestBody(description = "Datos de cuarentena", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpQuarantineRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.quarantineBatch(request));
    }

    @PostMapping("/tools/report-incident")
    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @Operation(summary = "Reportar incidencia", description = "Crea una incidencia operativa desde MCP para su seguimiento.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Incidencia creada"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos para ejecutar la accion")
    })
    public ResponseEntity<Map<String, Object>> reportIncident(@RequestBody(description = "Datos de incidencia", required = true, content = @Content()) @org.springframework.web.bind.annotation.RequestBody McpIncidentRequest request) {
        return ResponseEntity.ok(mcpToolWriteService.reportIncident(request));
    }
}
