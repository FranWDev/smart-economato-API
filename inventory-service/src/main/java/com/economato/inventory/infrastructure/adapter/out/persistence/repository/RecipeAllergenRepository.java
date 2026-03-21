package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.domain.model.Allergen;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeAllergen;

import java.util.List;

public interface RecipeAllergenRepository extends JpaRepository<RecipeAllergen, Integer> {
    @Override
    @EntityGraph(attributePaths = { "recipe", "allergen" })
    List<RecipeAllergen> findAll();

    @Override
    @EntityGraph(attributePaths = { "recipe", "allergen" })
    Page<RecipeAllergen> findAll(Pageable pageable);

    List<RecipeAllergen> findByRecipe(Recipe recipe);

    List<RecipeAllergen> findByAllergen(Allergen allergen);

    List<RecipeAllergen> findByRecipeAndAllergen(Recipe recipe, Allergen allergen);

    @Query("SELECT ra FROM RecipeAllergen ra JOIN FETCH ra.allergen WHERE ra.recipe = :recipe")
    List<RecipeAllergen> findByRecipeWithAllergen(@Param("recipe") Recipe recipe);

    @Query("SELECT ra FROM RecipeAllergen ra JOIN FETCH ra.recipe WHERE ra.allergen = :allergen")
    List<RecipeAllergen> findByAllergenWithRecipe(@Param("allergen") Allergen allergen);

    @Query("SELECT ra FROM RecipeAllergen ra JOIN FETCH ra.allergen JOIN FETCH ra.recipe WHERE ra.recipe = :recipe AND ra.allergen = :allergen")
    List<RecipeAllergen> findByRecipeAndAllergenWithDetails(@Param("recipe") Recipe recipe,
            @Param("allergen") Allergen allergen);
}