package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.Allergen;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeAllergen;

import java.util.List;

public interface RecipeAllergenRepository extends JpaRepository<RecipeAllergen, Integer> {
    List<RecipeAllergen> findByRecipe(Recipe recipe);
    List<RecipeAllergen> findByAllergen(Allergen allergen);
    List<RecipeAllergen> findByRecipeAndAllergen(Recipe recipe, Allergen allergen);
}