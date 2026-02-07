package com.economato.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.response.InventoryMovementResponseDTO;
import com.economato.inventory.service.InventoryAuditService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/inventory-audits")
@Tag(name = "Auditoria de inventario", description = "Operaciones relacionadas con auditorías de inventario, incluyendo movimientos de entrada y salida.")
public class InventoryAuditController {

    private final InventoryAuditService movementService;

    public InventoryAuditController(InventoryAuditService movementService) {
        this.movementService = movementService;
    }

    @Operation(
        summary = "Obtener todos los movimientos de inventario",
        description = "Devuelve una lista paginada de todos los registros de auditoría de inventario. Solo disponible para usuarios con rol ADMIN o CHEF. [Rol requerido: ADMIN]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de movimientos obtenida correctamente",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = InventoryMovementResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: el usuario no tiene permisos suficientes")
        }
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryMovementResponseDTO>> getAll(
            @Parameter(description = "Información de paginación") Pageable pageable) {
        return ResponseEntity.ok(movementService.findAll(pageable));
    }

    @Operation(
        summary = "Obtener movimiento por ID",
        description = "Devuelve los detalles de un movimiento de inventario específico a partir de su identificador. [Rol requerido: ADMIN]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Movimiento encontrado correctamente"),
            @ApiResponse(responseCode = "404", description = "No se encontró el movimiento con el ID especificado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: el usuario no tiene permisos suficientes")
        }
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryMovementResponseDTO> getById(
            @Parameter(description = "ID del movimiento de inventario", example = "45") @PathVariable Integer id) {
        return movementService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Obtener movimientos por tipo",
        description = "Devuelve todos los movimientos de inventario filtrados por tipo: ENTRADA o SALIDA. [Rol requerido: ADMIN]",
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista filtrada correctamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: el usuario no tiene permisos suficientes")
        }
    )
    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryMovementResponseDTO>> getByType(
            @Parameter(description = "Tipo de movimiento (ENTRADA o SALIDA)", example = "ENTRADA") @PathVariable String type) {
        return ResponseEntity.ok(movementService.findByMovementType(type));
    }

    @Operation(
        summary = "Obtener movimientos por rango de fechas",
        description = "Filtra los movimientos de inventario ocurridos entre dos fechas específicas (formato ISO-8601). [Rol requerido: ADMIN]",
        parameters = {
            @Parameter(name = "start", description = "Fecha de inicio (formato: 2025-03-01T00:00:00)", required = true),
            @Parameter(name = "end", description = "Fecha de fin (formato: 2025-03-31T23:59:59)", required = true)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Lista de movimientos en el rango indicado obtenida correctamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado: el usuario no tiene permisos suficientes")
        }
    )
    @GetMapping("/by-date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryMovementResponseDTO>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(movementService.findByMovementDateBetween(start, end));
    }
}
