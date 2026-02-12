package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.dto.projection.RecipeComponentProjection;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeComponent;

import java.util.List;
import java.util.Optional;

public interface RecipeComponentRepository extends JpaRepository<RecipeComponent, Integer> {

       boolean existsByProductId(Integer productId);

       List<RecipeComponent> findByParentRecipe(Recipe parentRecipe);

       @Query("SELECT rc FROM RecipeComponent rc WHERE rc.product.type = :productType")
       List<RecipeComponent> findByProductType(@Param("productType") String productType);

       @Query("SELECT rc FROM RecipeComponent rc LEFT JOIN FETCH rc.product LEFT JOIN FETCH rc.parentRecipe WHERE rc.id = :id")
       Optional<RecipeComponent> findWithRecipeAndProductById(@Param("id") Integer id);

       @Query("SELECT c FROM RecipeComponent c " +
                     "JOIN FETCH c.parentRecipe " +
                     "JOIN FETCH c.product " +
                     "WHERE c.parentRecipe.id = :recipeId")
       List<RecipeComponent> findAllByRecipeIdWithRelations(@Param("recipeId") Integer recipeId);

       // --- Proyecciones ---

       Page<RecipeComponentProjection> findAllProjectedBy(Pageable pageable);

       Optional<RecipeComponentProjection> findProjectedById(Integer id);

       List<RecipeComponentProjection> findProjectedByParentRecipeId(Integer recipeId);

}