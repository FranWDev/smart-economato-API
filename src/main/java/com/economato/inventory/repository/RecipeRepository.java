package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.Recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

        Optional<Recipe> findByName(String name);

        List<Recipe> findByNameContainingIgnoreCase(String namePart);

        List<Recipe> findByTotalCostLessThan(BigDecimal maxCost);

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "WHERE r.id = :id")
        Optional<Recipe> findByIdWithDetails(@Param("id") Integer id);

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product")
        List<Recipe> findAllWithDetails();

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
        List<Recipe> findByNameContainingIgnoreCaseWithDetails(@Param("namePart") String namePart);

        @Query("SELECT DISTINCT r FROM Recipe r " +
                        "LEFT JOIN FETCH r.components c " +
                        "LEFT JOIN FETCH c.product " +
                        "WHERE r.totalCost < :maxCost")
        List<Recipe> findByTotalCostLessThanWithDetails(@Param("maxCost") BigDecimal maxCost);

        @EntityGraph(attributePaths = { "components", "components.product" })
        Page<Recipe> findAll(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product" })
        @Query("SELECT r FROM Recipe r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
        Page<Recipe> findByNameContainingIgnoreCaseWithDetailsPageable(
                        @Param("namePart") String namePart,
                        Pageable pageable);

        // --- Proyecciones ---

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Page<com.economato.inventory.dto.projection.RecipeProjection> findByIsHiddenFalse(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Page<com.economato.inventory.dto.projection.RecipeProjection> findByIsHiddenTrue(Pageable pageable);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        Optional<com.economato.inventory.dto.projection.RecipeProjection> findProjectedById(Integer id);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        List<com.economato.inventory.dto.projection.RecipeProjection> findByNameContainingIgnoreCaseAndIsHiddenFalse(
                        String namePart);

        @EntityGraph(attributePaths = { "components", "components.product", "allergens" })
        List<com.economato.inventory.dto.projection.RecipeProjection> findByTotalCostLessThanAndIsHiddenFalse(
                        BigDecimal maxCost);
}
