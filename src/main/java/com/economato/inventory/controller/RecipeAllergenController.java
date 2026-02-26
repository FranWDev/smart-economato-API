package com.economato.inventory.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.model.RecipeAllergen;
import com.economato.inventory.service.RecipeAllergenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/recipe-allergens")
@Tag(name = "Alérgenos de Recetas", description = "Operaciones relacionadas con los alérgenos de las recetas")
public class RecipeAllergenController {

    private final RecipeAllergenService recipeAllergenService;

    public RecipeAllergenController(
            RecipeAllergenService recipeAllergenService) {
        this.recipeAllergenService = recipeAllergenService;
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener todas las relaciones receta-alérgeno",
               description = "Devuelve una lista paginada de todas las asociaciones entre recetas y alérgenos. [Rol requerido: USER]")
    @ApiResponse(responseCode = "200", description = "Lista de relaciones obtenida correctamente",
                 content = @Content(mediaType = "application/json",
                 schema = @Schema(implementation = RecipeAllergen.class)))
    public List<RecipeAllergen> getAll(Pageable pageable) {
        return recipeAllergenService.findAll(pageable);
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @PostMapping
    @Operation(summary = "Crear una nueva relación receta-alérgeno",
               description = "Asocia un alérgeno existente a una receta. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relación creada correctamente",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = RecipeAllergen.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos")
    })
    public RecipeAllergen create(
            @Parameter(description = "Objeto que contiene los IDs de receta y alérgeno a asociar", required = true)
            @RequestBody RecipeAllergen recipeAllergen) {
        return recipeAllergenService.save(recipeAllergen);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una relación receta-alérgeno por ID",
               description = "Elimina la asociación entre una receta y un alérgeno específico. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Relación eliminada correctamente"),
        @ApiResponse(responseCode = "404", description = "Relación no encontrada")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID de la relación receta-alérgeno", example = "3", required = true)
            @PathVariable Integer id) {
        return recipeAllergenService.findById(id)
                .map(existing -> {
                    recipeAllergenService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @GetMapping("/recipe/{recipeId}")
    @Operation(summary = "Obtener alérgenos por receta",
               description = "Devuelve todos los alérgenos asociados a una receta específica. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de alérgenos asociados a la receta",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = RecipeAllergen.class))),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<List<RecipeAllergen>> getByRecipe(
            @Parameter(description = "ID de la receta", example = "12", required = true)
            @PathVariable Integer recipeId) {
        return recipeAllergenService.getByRecipeId(recipeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @GetMapping("/allergen/{allergenId}")
    @Operation(summary = "Obtener recetas por alérgeno",
               description = "Devuelve todas las recetas que contienen el alérgeno especificado. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de relaciones encontradas",
                     content = @Content(mediaType = "application/json",
                     schema = @Schema(implementation = RecipeAllergen.class))),
        @ApiResponse(responseCode = "404", description = "Alérgeno no encontrado")
    })
    public ResponseEntity<List<RecipeAllergen>> getByAllergen(
            @Parameter(description = "ID del alérgeno", example = "5", required = true)
            @PathVariable Integer allergenId) {
        return recipeAllergenService.getByAllergenId(allergenId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @PostMapping("/{recipeId}/{allergenId}")
    @Operation(summary = "Asociar un alérgeno a una receta",
               description = "Crea una asociación entre una receta y un alérgeno existentes. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alérgeno asociado correctamente"),
        @ApiResponse(responseCode = "404", description = "Receta o alérgeno no encontrado")
    })
    public ResponseEntity<Void> addAllergenToRecipe(
            @Parameter(description = "ID de la receta", required = true)
            @PathVariable Integer recipeId,
            @Parameter(description = "ID del alérgeno", required = true)
            @PathVariable Integer allergenId) {
        
        boolean success = recipeAllergenService.addAllergenToRecipe(recipeId, allergenId);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasAnyRole('CHEF', 'ELEVATED', 'ADMIN')")
    @DeleteMapping("/recipe/{recipeId}/allergen/{allergenId}")
    @Operation(summary = "Eliminar asociación entre receta y alérgeno",
               description = "Elimina la asociación entre una receta específica y un alérgeno. [Rol requerido: CHEF]")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Asociación eliminada correctamente"),
        @ApiResponse(responseCode = "404", description = "Receta o alérgeno no encontrado")
    })
    public ResponseEntity<Void> removeAllergenFromRecipe(
            @Parameter(description = "ID de la receta", required = true)
            @PathVariable Integer recipeId,
            @Parameter(description = "ID del alérgeno", required = true)
            @PathVariable Integer allergenId) {
        
        boolean success = recipeAllergenService.removeAllergenFromRecipe(recipeId, allergenId);
        return success ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasAnyRole('USER', 'CHEF', 'ELEVATED', 'ADMIN')")
    @GetMapping("/recipe/{recipeId}/allergens")
    @Operation(summary = "Obtener alérgenos de una receta",
               description = "Devuelve la lista de todos los alérgenos asociados a una receta. [Rol requerido: USER]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de alérgenos obtenida correctamente"),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<List<AllergenResponseDTO>> getAllergensForRecipe(
            @Parameter(description = "ID de la receta", required = true)
            @PathVariable Integer recipeId) {
        
        return recipeAllergenService.getAllergensForRecipe(recipeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}