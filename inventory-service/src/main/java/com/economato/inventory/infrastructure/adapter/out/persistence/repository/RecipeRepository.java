package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.RecipeProjection;
import com.economato.inventory.domain.model.Recipe;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Optional<Recipe> findByName(String name);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        @Query("SELECT DISTINCT r FROM Recipe r WHERE r.isHidden = false")
        List<Recipe> findAllVisibleWithDetails();

        List<Recipe> findByNameContainingIgnoreCase(String namePart);

        List<Recipe> findByTotalCostLessThan(BigDecimal maxCost);

        long countByIsHiddenFalse();

        @Query("SELECT COUNT(r) FROM Recipe r WHERE r.allergens IS NOT EMPTY AND r.isHidden = false")
        long countWithAllergens();

        @Query("SELECT COUNT(r) FROM Recipe r WHERE r.allergens IS EMPTY AND r.isHidden = false")
        long countWithoutAllergens();

        @Query("SELECT AVG(r.totalCost) FROM Recipe r WHERE r.isHidden = false")
        BigDecimal getAveragePrice();

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "LEFT JOIN FETCH r.allergens " +
                        "WHERE r.id = :id")
        Optional<Recipe> findByIdWithDetails(@Param("id") Integer id);

        @Query("SELECT c.product.id FROM Recipe r JOIN r.components c WHERE r.id = :recipeId")
        List<Integer> findProductIdsByRecipeId(@Param("recipeId") Integer recipeId);

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "LEFT JOIN FETCH r.allergens")
        List<Recipe> findAllWithDetails();

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "LEFT JOIN FETCH r.allergens " +
                        "WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
        List<Recipe> findByNameContainingIgnoreCaseWithDetails(@Param("namePart") String namePart);

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "LEFT JOIN FETCH r.allergens " +
                        "WHERE r.totalCost < :maxCost")
        List<Recipe> findByTotalCostLessThanWithDetails(@Param("maxCost") BigDecimal maxCost);

        @EntityGraph(attributePaths = { "components", "components.product" })
        Page<Recipe> findAll(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product" })
        @Query("SELECT r FROM Recipe r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
        Page<Recipe> findByNameContainingIgnoreCaseWithDetailsPageable(
                        @Param("namePart") String namePart,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Page<RecipeProjection> findByIsHiddenFalse(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Page<RecipeProjection> findByIsHiddenTrue(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Optional<RecipeProjection> findProjectedById(Integer id);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        List<RecipeProjection> findByNameContainingIgnoreCaseAndIsHiddenFalse(String namePart);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        List<RecipeProjection> findByTotalCostLessThanAndIsHiddenFalse(BigDecimal maxCost);

        @Query("SELECT DISTINCT r FROM Recipe r LEFT JOIN FETCH r.allergens WHERE r.isHidden = false")
        List<Recipe> findAllWithAllergens();

        @Query("SELECT DISTINCT r FROM Recipe r LEFT JOIN FETCH r.allergens WHERE r.id IN :ids")
        List<Recipe> findAllByIdWithAllergens(@Param("ids") Collection<Integer> ids);
}
