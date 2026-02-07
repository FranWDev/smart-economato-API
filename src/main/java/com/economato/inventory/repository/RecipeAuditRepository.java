package com.economato.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.RecipeAudit;

public interface RecipeAuditRepository extends JpaRepository<RecipeAudit, Integer> {

    List<RecipeAudit> findByRecipeId(Integer id);

	List<RecipeAudit> findByUsersId(Integer id);

    List<RecipeAudit> findByAuditDateBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT ra FROM RecipeAudit ra " +
           "LEFT JOIN FETCH ra.recipe " +
           "LEFT JOIN FETCH ra.users " +
           "WHERE ra.recipe.id = :recipeId")
    List<RecipeAudit> findByRecipeIdWithDetails(@Param("recipeId") Integer recipeId);

    @Query("SELECT ra FROM RecipeAudit ra " +
           "LEFT JOIN FETCH ra.recipe " +
           "LEFT JOIN FETCH ra.users " +
           "WHERE ra.auditDate BETWEEN :start AND :end")
    List<RecipeAudit> findByAuditDateBetweenWithDetails(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
