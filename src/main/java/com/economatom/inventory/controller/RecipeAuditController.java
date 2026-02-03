package com.economatom.inventory.controller;

import com.economatom.inventory.dto.response.RecipeAuditResponseDTO;
import com.economatom.inventory.service.RecipeAuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/recipe-audits")
@Tag(name = "Auditoría de Recetas", description = "Operaciones relacionadas con las auditorías de recetas")
public class RecipeAuditController {

    private final RecipeAuditService recipeAuditService;

    public RecipeAuditController(RecipeAuditService recipeAuditService) {
        this.recipeAuditService = recipeAuditService;
    }

    @GetMapping
    @Operation(summary = "Obtener todas las auditorías de recetas",
               description = "Devuelve una lista paginada de todas las auditorías registradas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías obtenida correctamente",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeAuditResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeAuditResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(recipeAuditService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener auditoría por ID",
               description = "Devuelve una auditoría específica según su identificador.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Auditoría encontrada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeAuditResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Auditoría no encontrada")
    })
    public ResponseEntity<RecipeAuditResponseDTO> getById(
            @Parameter(description = "ID de la auditoría", example = "5", required = true)
            @PathVariable Integer id) {
        return recipeAuditService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-recipe/{id}")
    @Operation(summary = "Obtener auditorías por receta",
               description = "Devuelve todas las auditorías asociadas a una receta específica.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías por receta",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeAuditResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<List<RecipeAuditResponseDTO>> getByRecipeId(
            @Parameter(description = "ID de la receta", example = "12", required = true)
            @PathVariable Integer id) {
        return ResponseEntity.ok(recipeAuditService.findByRecipeId(id));
    }

    @GetMapping("/by-user/{id}")
    @Operation(summary = "Obtener auditorías por usuario",
               description = "Devuelve todas las auditorías registradas por un usuario específico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías por usuario",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeAuditResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<List<RecipeAuditResponseDTO>> getByUserId(
            @Parameter(description = "ID del usuario", example = "3", required = true)
            @PathVariable Integer id) {
        return ResponseEntity.ok(recipeAuditService.findByUserId(id));
    }

    @GetMapping("/by-date-range")
    @Operation(summary = "Obtener auditorías por rango de fechas",
               description = "Devuelve todas las auditorías realizadas dentro del rango de fechas indicado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías encontradas en el rango",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeAuditResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Fechas inválidas o mal formateadas")
    })
    public ResponseEntity<List<RecipeAuditResponseDTO>> getByDateRange(
            @Parameter(description = "Fecha de inicio (ISO 8601)", example = "2025-10-01T00:00:00", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "Fecha de fin (ISO 8601)", example = "2025-10-18T23:59:59", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(recipeAuditService.findByMovementDateBetween(start, end));
    }
}
