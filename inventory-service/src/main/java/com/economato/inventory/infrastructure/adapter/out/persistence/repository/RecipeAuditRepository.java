package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.RecipeAuditProjection;
import com.economato.inventory.domain.model.RecipeAudit;

public interface RecipeAuditRepository extends JpaRepository<RecipeAudit, Integer> {

        @EntityGraph(attributePaths = {"recipe", "user"})
        Optional<RecipeAudit> findById(Integer id);

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

        Page<RecipeAuditProjection> findAllProjectedBy(Pageable pageable);

        List<RecipeAuditProjection> findProjectedByRecipeId(Integer recipeId);

        List<RecipeAuditProjection> findProjectedByUserId(Integer userId);

        List<RecipeAuditProjection> findProjectedByAuditDateBetween(LocalDateTime start, LocalDateTime end);
}
