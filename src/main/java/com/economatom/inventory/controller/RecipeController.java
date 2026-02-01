package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.RecipeRequestDTO;
import com.economatom.inventory.dto.response.RecipeResponseDTO;
import com.economatom.inventory.service.RecipeService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@CrossOrigin(origins = "*")
@Tag(name = "Recetas", description = "Operaciones relacionadas con las recetas")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    @Operation(summary = "Obtener todas las recetas", description = "Devuelve una lista paginada de todas las recetas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de recetas",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(recipeService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener receta por ID", description = "Devuelve los datos de una receta específica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Receta encontrada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<RecipeResponseDTO> getById(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id) {
        return recipeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Crear receta", description = "Crea una nueva receta")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Receta creada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeResponseDTO.class)))
    })
    public ResponseEntity<RecipeResponseDTO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos de la receta a crear",
                required = true,
                content = @Content(schema = @Schema(implementation = RecipeRequestDTO.class))
            )
            @Valid @RequestBody RecipeRequestDTO recipeRequest) {
        RecipeResponseDTO created = recipeService.save(recipeRequest);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar receta", 
               description = "Actualiza los datos de una receta existente. " +
                           "Este endpoint utiliza **bloqueo optimista** mediante control de versión para detectar " +
                           "actualizaciones concurrentes. Si otro usuario modifica la receta simultáneamente, " +
                           "se generará un error de concurrencia.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Receta actualizada",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada"),
        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia - La receta fue modificada por otro usuario")
    })
    public ResponseEntity<RecipeResponseDTO> update(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos de la receta a actualizar",
                required = true,
                content = @Content(schema = @Schema(implementation = RecipeRequestDTO.class))
            )
            @Valid @RequestBody RecipeRequestDTO recipeRequest) {
        return recipeService.update(id, recipeRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar receta", description = "Elimina una receta por su ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Receta eliminada"),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<Object> delete(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer id) {
        return recipeService.findById(id)
                .map(existing -> {
                    recipeService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar recetas por nombre", description = "Devuelve todas las recetas cuyo nombre contenga la cadena indicada")
    public List<RecipeResponseDTO> searchByName(
            @Parameter(description = "Nombre a buscar", required = true) @RequestParam String name) {
        return recipeService.findByNameContaining(name);
    }

    @GetMapping("/maxcost")
    @Operation(summary = "Buscar recetas por costo máximo", description = "Devuelve todas las recetas cuyo costo total sea menor al indicado")
    public List<RecipeResponseDTO> findByCostLessThan(
            @Parameter(description = "Costo máximo", required = true) @RequestParam BigDecimal maxCost) {
        return recipeService.findByCostLessThan(maxCost);
    }
}
