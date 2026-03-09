package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.RecipeAuditProjection;
import com.economato.inventory.domain.model.RecipeAudit;

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
        Page<RecipeAuditProjection> findAllProjectedBy(Pageable pageable);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.recipe.id = :recipeId")
        List<RecipeAuditProjection> findProjectedByRecipeId(@Param("recipeId") Integer recipeId);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.user.id = :userId")
        List<RecipeAuditProjection> findProjectedByUserId(@Param("userId") Integer userId);

        @Query("SELECT ra FROM RecipeAudit ra WHERE ra.auditDate BETWEEN :start AND :end")
        List<RecipeAuditProjection> findProjectedByAuditDateBetween(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);
}
