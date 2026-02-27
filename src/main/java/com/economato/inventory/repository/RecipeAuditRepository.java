package com.economato.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.RecipeAudit;

public interface RecipeAuditRepository extends JpaRepository<RecipeAudit, Integer> {

        List<RecipeAudit> findByRecipeId(Integer id);

        List<RecipeAudit> findByUserId(Integer id);

        List<RecipeAudit> findByAuditDateBetween(LocalDateTime start, LocalDateTime end);

        @Query("SELECT ra FROM RecipeAudit ra " +
                        "LEFT JOIN FETCH ra.recipe " +
                        "LEFT JOIN FETCH ra.user " +
                        "WHERE ra.recipe.id = :recipeId")
        List<RecipeAudit> findByRecipeIdWithDetails(@Param("recipeId") Integer recipeId);

        @Query("SELECT ra FROM RecipeAudit ra " +
                        "LEFT JOIN FETCH ra.recipe " +
                        "LEFT JOIN FETCH ra.user " +
                        "WHERE ra.auditDate BETWEEN :start AND :end")
        List<RecipeAudit> findByAuditDateBetweenWithDetails(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        // --- Proyecciones ---

        @Query("SELECT ra FROM RecipeAudit ra")
        org.springframework.data.domain.Page<com.economato.inventory.dto.projection.RecipeAuditProjection> findAllProjectedBy(
                        org.springframework.data.domain.Pageable pageable);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.recipe.id = :recipeId")
        List<com.economato.inventory.dto.projection.RecipeAuditProjection> findProjectedByRecipeId(
                        @Param("recipeId") Integer recipeId);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.user.id = :userId")
        List<com.economato.inventory.dto.projection.RecipeAuditProjection> findProjectedByUserId(
                        @Param("userId") Integer userId);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.auditDate BETWEEN :start AND :end")
        List<com.economato.inventory.dto.projection.RecipeAuditProjection> findProjectedByAuditDateBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);
}
