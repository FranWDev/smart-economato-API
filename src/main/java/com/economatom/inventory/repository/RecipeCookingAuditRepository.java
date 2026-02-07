package com.economatom.inventory.repository;

import com.economatom.inventory.model.RecipeCookingAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecipeCookingAuditRepository extends JpaRepository<RecipeCookingAudit, Long> {

    @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.recipe.id = :recipeId ORDER BY rca.cookingDate DESC")
    List<RecipeCookingAudit> findByRecipeId(@Param("recipeId") Integer recipeId);

    @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.users.id = :userId ORDER BY rca.cookingDate DESC")
    List<RecipeCookingAudit> findByUserId(@Param("userId") Integer userId);

    @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.cookingDate BETWEEN :startDate AND :endDate ORDER BY rca.cookingDate DESC")
    List<RecipeCookingAudit> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rca FROM RecipeCookingAudit rca ORDER BY rca.cookingDate DESC")
    Page<RecipeCookingAudit> findAllOrderByDateDesc(Pageable pageable);
}
