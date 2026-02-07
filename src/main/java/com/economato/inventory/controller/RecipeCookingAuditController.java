package com.economato.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.service.RecipeCookingAuditService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador REST para consultar la auditoría de cocinado de recetas.
 */
@RestController
@RequestMapping("/api/recipe-cooking-audit")
@Tag(name = "Auditoría de Cocinado de Recetas", description = "Consulta de auditoría de cocinado de recetas")
public class RecipeCookingAuditController {

    private final RecipeCookingAuditService service;

    public RecipeCookingAuditController(RecipeCookingAuditService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener todas las auditorías de cocinado", 
               description = "Devuelve una lista paginada de todas las auditorías de cocinado de recetas. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeCookingAuditResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/recipe/{recipeId}")
    @Operation(summary = "Obtener auditorías por receta", 
               description = "Devuelve todas las auditorías de cocinado de una receta específica. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías de la receta",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeCookingAuditResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> getByRecipeId(
            @Parameter(description = "ID de la receta", required = true) 
            @PathVariable Integer recipeId) {
        return ResponseEntity.ok(service.findByRecipeId(recipeId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    @Operation(summary = "Obtener auditorías por usuario", 
               description = "Devuelve todas las auditorías de cocinado realizadas por un usuario específico. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías del usuario",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeCookingAuditResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> getByUserId(
            @Parameter(description = "ID del usuario", required = true) 
            @PathVariable Integer userId) {
        return ResponseEntity.ok(service.findByUserId(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/date-range")
    @Operation(summary = "Obtener auditorías por rango de fechas", 
               description = "Devuelve todas las auditorías de cocinado en un rango de fechas específico. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de auditorías en el rango",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeCookingAuditResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> getByDateRange(
            @Parameter(description = "Fecha de inicio (formato: yyyy-MM-dd'T'HH:mm:ss)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Fecha de fin (formato: yyyy-MM-dd'T'HH:mm:ss)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(service.findByDateRange(startDate, endDate));
    }
}
