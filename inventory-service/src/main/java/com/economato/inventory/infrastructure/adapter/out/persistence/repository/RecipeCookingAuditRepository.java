package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.economato.inventory.application.dto.projection.WeeklyIngredientConsumption;
import com.economato.inventory.domain.model.RecipeCookingAudit;

@Repository
public interface RecipeCookingAuditRepository extends JpaRepository<RecipeCookingAudit, Long> {

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.id = :id")
  Optional<RecipeCookingAudit> findByIdWithDetails(@Param("id") Long id);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.recipe.id = :recipeId ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByRecipeId(@Param("recipeId") Integer recipeId);

  @Query("SELECT rc.product.id FROM RecipeCookingAudit rca JOIN rca.recipe r JOIN r.components rc WHERE rca.id = :id")
  List<Integer> findProductIdsByAuditId(@Param("id") Long id);

  Optional<RecipeCookingAudit> findByCorrelationId(String correlationId);

    long countByRecipeIdAndCookingDateAfter(Integer recipeId, LocalDateTime since);

  @Query("SELECT rca FROM RecipeCookingAudit rca LEFT JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.user.id = :userId ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByUserId(@Param("userId") Integer userId);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE LOWER(rca.recipe.name) LIKE LOWER(CONCAT('%', :recipeName, '%')) ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByRecipeNameContainingIgnoreCase(@Param("recipeName") String recipeName);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByDateRange(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
  Stream<RecipeCookingAudit> streamByDateRange(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query(value = "SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user ORDER BY rca.cookingDate DESC",
         countQuery = "SELECT COUNT(rca) FROM RecipeCookingAudit rca")
  Page<RecipeCookingAudit> findAllOrderByDateDesc(Pageable pageable);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user ORDER BY rca.cookingDate DESC")
  Stream<RecipeCookingAudit> streamAllOrderByDateDesc();

    @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.user.id IN :userIds ORDER BY rca.cookingDate DESC")
    List<RecipeCookingAudit> findByUserIdInOrderByCookingDateDesc(@Param("userIds") Collection<Integer> userIds);

  /**
   * Devuelve el consumo semanal de cada ingrediente agrupado por semana natural.
   * El índice de semana es 0 para la semana más antigua dentro del rango.
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

  @Query(value = """
      SELECT product_id, recipe_name FROM (
          SELECT rc.product_id, r.recipe_name, SUM(rca.quantity_cooked * rc.quantity) as total,
                 ROW_NUMBER() OVER(PARTITION BY rc.product_id ORDER BY SUM(rca.quantity_cooked * rc.quantity) DESC) as rn
          FROM recipe_cooking_audit rca
          INNER JOIN recipe r       ON r.recipe_id = rca.recipe_id
          INNER JOIN recipe_component rc ON rc.parent_recipe_id = rca.recipe_id
          WHERE rc.product_id IN :productIds
            AND rca.cooking_date >= :since
          GROUP BY rc.product_id, r.recipe_id, r.recipe_name
      ) t WHERE rn <= 3
      """, nativeQuery = true)
  List<Object[]> findTopConsumingRecipesByProducts(
      @Param("productIds") List<Integer> productIds,
      @Param("since") LocalDateTime since);

  @Query("SELECT DISTINCT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user JOIN rca.recipe.components rc WHERE rc.product.id IN :productIds AND rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findAffectedCookingsByProductIdsAndDateRange(
      @Param("productIds") List<Integer> productIds,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query("SELECT rca FROM RecipeCookingAudit rca JOIN FETCH rca.recipe LEFT JOIN FETCH rca.user WHERE rca.correlationId IN (SELECT sl.correlationId FROM StockLedgerBatchDetail d JOIN d.ledgerTransaction sl WHERE d.batch.id = :batchId AND sl.correlationId IS NOT NULL) ORDER BY rca.cookingDate DESC")
  List<RecipeCookingAudit> findByBatchId(@Param("batchId") Long batchId);
}
