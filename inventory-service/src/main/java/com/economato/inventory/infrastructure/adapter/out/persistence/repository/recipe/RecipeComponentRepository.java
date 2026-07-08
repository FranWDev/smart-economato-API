package com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.recipe.projection.RecipeComponentProjection;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;

import java.util.List;
import java.util.Optional;

public interface RecipeComponentRepository extends JpaRepository<RecipeComponent, Integer> {

       boolean existsByProductId(Integer productId);

       List<RecipeComponent> findByParentRecipe(Recipe parentRecipe);

       @Query("SELECT rc FROM RecipeComponent rc LEFT JOIN FETCH rc.product LEFT JOIN FETCH rc.parentRecipe WHERE rc.id = :id")
       Optional<RecipeComponent> findWithRecipeAndProductById(@Param("id") Integer id);

       @Query("SELECT c FROM RecipeComponent c " +
                     "JOIN FETCH c.parentRecipe " +
                     "JOIN FETCH c.product " +
                     "WHERE c.parentRecipe.id = :recipeId")
       List<RecipeComponent> findAllByRecipeIdWithRelations(@Param("recipeId") Integer recipeId);

       Page<RecipeComponentProjection> findAllProjectedBy(Pageable pageable);

       Optional<RecipeComponentProjection> findProjectedById(Integer id);

       List<RecipeComponentProjection> findProjectedByParentRecipeId(Integer recipeId);

}