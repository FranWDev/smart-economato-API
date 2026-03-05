package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.dto.projection.WeeklyIngredientConsumption;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecipeCookingAuditRepository extends JpaRepository<RecipeCookingAudit, Long> {

  @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.recipe.id = :recipeId ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByRecipeId(@Param("recipeId") Integer recipeId);

  @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.user.id = :userId ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByUserId(@Param("userId") Integer userId);

  @Query("SELECT rca FROM RecipeCookingAudit rca WHERE LOWER(rca.recipe.name) LIKE LOWER(CONCAT('%', :recipeName, '%')) ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByRecipeNameContainingIgnoreCase(@Param("recipeName") String recipeName);

  @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByDateRange(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
  Stream<RecipeCookingAudit> streamByDateRange(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query("SELECT rca FROM RecipeCookingAudit rca ORDER BY rca.cookingDate DESC")
  Page<RecipeCookingAudit> findAllOrderByDateDesc(Pageable pageable);

  @Query("SELECT rca FROM RecipeCookingAudit rca ORDER BY rca.cookingDate DESC")
  Stream<RecipeCookingAudit> streamAllOrderByDateDesc();

  /**
   * Devuelve el consumo semanal de cada ingrediente agrupado por semana natural.
   * El índice de semana es 0 para la semana más antigua dentro del rango.
   *
   * @param since   fecha de inicio (normalmente NOW - 12 semanas)
   * @param refDate fecha de referencia para el cálculo del índice de semana
   *                (puede ser igual a {@code since} para empezar en índice 0)
   */
  @Query(value = """
      SELECT CAST(FLOOR(EXTRACT(EPOCH FROM (rca.cooking_date - :refDate)) / 86400 / 7) AS INTEGER) AS weekIndex,
             rc.product_id                                                        AS productId,
             SUM(rca.quantity_cooked * rc.quantity)                               AS totalConsumed
      FROM recipe_cooking_audit rca
      INNER JOIN recipe_component rc ON rc.parent_recipe_id = rca.recipe_id
      WHERE rca.cooking_date >= :since
      GROUP BY weekIndex, rc.product_id
      ORDER BY weekIndex ASC
      """, nativeQuery = true)
  List<WeeklyIngredientConsumption> findWeeklyConsumptionPerIngredient(
      @Param("since") LocalDateTime since,
      @Param("refDate") LocalDateTime refDate);

  /**
   * Devuelve los nombres de las recetas que más consumen un ingrediente
   * en el período analizado, ordenadas por consumo total descendente.
   */
  @Query(value = """
      SELECT r.recipe_name
      FROM recipe_cooking_audit rca
      INNER JOIN recipe r       ON r.recipe_id = rca.recipe_id
      INNER JOIN recipe_component rc ON rc.parent_recipe_id = rca.recipe_id
      WHERE rc.product_id = :productId
        AND rca.cooking_date >= :since
      GROUP BY r.recipe_id, r.recipe_name
      ORDER BY SUM(rca.quantity_cooked * rc.quantity) DESC
      LIMIT 3
      """, nativeQuery = true)
  List<String> findTopConsumingRecipesByProduct(
      @Param("productId") Integer productId,
      @Param("since") LocalDateTime since);
}
