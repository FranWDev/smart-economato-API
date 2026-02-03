package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.RecipeComponentRequestDTO;
import com.economatom.inventory.dto.response.RecipeComponentResponseDTO;
import com.economatom.inventory.service.RecipeComponentService;
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

import java.util.List;

@RestController
@RequestMapping("/api/recipe-components")
@Tag(name = "Componentes de Recetas", description = "Operaciones relacionadas con los componentes de recetas")
public class RecipeComponentController {

    private final RecipeComponentService componentService;
    private final RecipeService recipeService;

    public RecipeComponentController(RecipeComponentService componentService, RecipeService recipeService) {
        this.componentService = componentService;
        this.recipeService = recipeService;
    }

    @GetMapping
    @Operation(summary = "Obtener todos los componentes", description = "Devuelve una lista paginada de todos los componentes de recetas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de componentes",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeComponentResponseDTO.class)))
    })
    public ResponseEntity<List<RecipeComponentResponseDTO>> getAll(Pageable pageable) {
        return ResponseEntity.status(200).body(componentService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener componente por ID", description = "Devuelve un componente de receta específico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Componente encontrado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeComponentResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Componente no encontrado")
    })
    public ResponseEntity<RecipeComponentResponseDTO> getById(
            @Parameter(description = "ID del componente", required = true) @PathVariable Integer id) {
        return componentService.findById(id)
                .map(component -> ResponseEntity.status(200).body(component))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recipe/{recipeId}")
    @Operation(summary = "Crear componente", description = "Crea un nuevo componente para una receta específica")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Componente creado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeComponentResponseDTO.class)))
    })
    public ResponseEntity<RecipeComponentResponseDTO> create(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer recipeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos del componente a crear",
                required = true,
                content = @Content(schema = @Schema(implementation = RecipeComponentRequestDTO.class))
            )
            @Valid @RequestBody RecipeComponentRequestDTO componentRequest) {
        componentRequest.setRecipeId(recipeId);
        return ResponseEntity.status(201).body(componentService.save(componentRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar componente", description = "Actualiza un componente de receta existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Componente actualizado",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeComponentResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Componente no encontrado")
    })
    public ResponseEntity<RecipeComponentResponseDTO> update(
            @Parameter(description = "ID del componente", required = true) @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos del componente a actualizar",
                required = true,
                content = @Content(schema = @Schema(implementation = RecipeComponentRequestDTO.class))
            )
            @Valid @RequestBody RecipeComponentRequestDTO componentRequest) {
        return componentService.update(id, componentRequest)
                .map(component -> ResponseEntity.status(200).body(component))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar componente", description = "Elimina un componente de receta por su ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Componente eliminado"),
        @ApiResponse(responseCode = "404", description = "Componente no encontrado")
    })
    public ResponseEntity<Object> delete(
            @Parameter(description = "ID del componente", required = true) @PathVariable Integer id) {
        return componentService.findById(id)
                .map(existing -> {
                    componentService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recipe/{recipeId}")
    @Operation(summary = "Obtener componentes por receta", description = "Devuelve todos los componentes de una receta específica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de componentes",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RecipeComponentResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Receta no encontrada")
    })
    public ResponseEntity<List<RecipeComponentResponseDTO>> getByParentRecipe(
            @Parameter(description = "ID de la receta", required = true) @PathVariable Integer recipeId) {
        return recipeService.findById(recipeId)
                .map(recipe -> ResponseEntity.status(200).body(componentService.findByParentRecipe(recipe)))
                .orElse(ResponseEntity.notFound().build());
    }
}
