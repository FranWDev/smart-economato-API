package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.model.Allergen;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeAllergen;

import java.util.List;

public interface RecipeAllergenRepository extends JpaRepository<RecipeAllergen, Integer> {
    List<RecipeAllergen> findByRecipe(Recipe recipe);
    List<RecipeAllergen> findByAllergen(Allergen allergen);
    List<RecipeAllergen> findByRecipeAndAllergen(Recipe recipe, Allergen allergen);
}