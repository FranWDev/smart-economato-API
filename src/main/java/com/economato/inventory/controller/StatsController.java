package com.economato.inventory.controller;

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
@Tag(name = "Statistics", description = "Endpoints for general application statistics")
@PreAuthorize("hasRole('ADMIN')")
public class StatsController {

    private final RecipeService recipeService;
    private final UserService userService;

    @GetMapping("/recipes")
    @Operation(summary = "Get recipe statistics", description = "Returns total count, count with/without allergens, and average price")
    public ResponseEntity<RecipeStatsResponseDTO> getRecipeStats() {
        return ResponseEntity.ok(recipeService.getRecipeStats());
    }

    @GetMapping("/users")
    @Operation(summary = "Get user statistics", description = "Returns total count and counts grouped by role")
    public ResponseEntity<UserStatsResponseDTO> getUserStats() {
        return ResponseEntity.ok(userService.getUserStats());
    }
}
