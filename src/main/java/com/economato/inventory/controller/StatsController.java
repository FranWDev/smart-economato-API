package com.economato.inventory.controller;

import com.economato.inventory.dto.response.RecipeAverageCostResponseDTO;
import com.economato.inventory.dto.response.RecipeCountResponseDTO;
import com.economato.inventory.dto.response.RecipeStatsResponseDTO;
import com.economato.inventory.dto.response.UserStatsResponseDTO;
import com.economato.inventory.service.RecipeService;
import com.economato.inventory.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Estadísticas", description = "Endpoints para obtener estadísticas generales de la aplicación")
@PreAuthorize("hasRole('ADMIN')")
public class StatsController {

    private final RecipeService recipeService;
    private final UserService userService;

    @GetMapping("/recipes")
    @Operation(summary = "Obtener estadísticas de las recetas", description = "Devuelve estadísticas de las recetas como el precio promedio, "
            + "cantidad de recetas con alérgenos y sin alérgenos, y la cantidad total de recetas [Rol requerido: ADMIN]")
    public ResponseEntity<RecipeStatsResponseDTO> getRecipeStats() {
        return ResponseEntity.ok(recipeService.getRecipeStats());
    }

    @GetMapping("/recipes/with-allergens/count")
    @Operation(summary = "Obtener cantidad de recetas con alérgenos", description = "Devuelve la cantidad total de recetas visibles que tienen alérgenos [Rol requerido: ADMIN]")
    public ResponseEntity<RecipeCountResponseDTO> getRecipesWithAllergensCount() {
        return ResponseEntity.ok(recipeService.getRecipesWithAllergensCount());
    }

    @GetMapping("/recipes/without-allergens/count")
    @Operation(summary = "Obtener cantidad de recetas sin alérgenos", description = "Devuelve la cantidad total de recetas visibles que no tienen alérgenos [Rol requerido: ADMIN]")
    public ResponseEntity<RecipeCountResponseDTO> getRecipesWithoutAllergensCount() {
        return ResponseEntity.ok(recipeService.getRecipesWithoutAllergensCount());
    }

    @GetMapping("/recipes/average-cost")
    @Operation(summary = "Obtener costo promedio de recetas", description = "Devuelve el costo promedio de las recetas visibles [Rol requerido: ADMIN]")
    public ResponseEntity<RecipeAverageCostResponseDTO> getRecipesAverageCost() {
        return ResponseEntity.ok(recipeService.getRecipesAverageCost());
    }

    @GetMapping("/users")
    @Operation(summary = "Obtener estadísticas de los usuarios", description = "Devuelve estadísticas de los usuarios como la cantidad total "
            + "de usuarios y la cantidad de usuarios por rol [Rol requerido: ADMIN]")
    public ResponseEntity<UserStatsResponseDTO> getUserStats() {
        return ResponseEntity.ok(userService.getUserStats());
    }
}
