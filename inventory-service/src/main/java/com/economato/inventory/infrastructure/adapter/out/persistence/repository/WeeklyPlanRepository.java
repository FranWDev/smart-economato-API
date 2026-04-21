package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.economato.inventory.domain.model.WeeklyPlan;
import com.economato.inventory.domain.model.WeeklyPlanStatus;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, Long> {

    List<WeeklyPlan> findByChefIdAndStatus(Integer chefId, WeeklyPlanStatus status);

    Optional<WeeklyPlan> findByChefIdAndWeekStartDate(Integer chefId, LocalDate weekStartDate);

    List<WeeklyPlan> findByChefId(Integer chefId);

    List<WeeklyPlan> findByStatus(WeeklyPlanStatus status);

    Page<WeeklyPlan> findByChefId(Integer chefId, Pageable pageable);

   @Query("SELECT DISTINCT wp FROM WeeklyPlan wp JOIN FETCH wp.slots s WHERE wp.status = 'ACTIVE' AND s.status IN ('PENDING', 'IN_PROGRESS')")
    List<WeeklyPlan> findActivePlansWithPendingSlots();

    @Query("SELECT wp FROM WeeklyPlan wp JOIN FETCH wp.slots s " +
           "WHERE wp.chef.id = :chefId AND wp.status = 'ACTIVE' " +
           "AND s.status = 'PENDING' AND s.startTime < :currentTime")
    List<WeeklyPlan> findActivePlansWithPastPendingSlots(@Param("chefId") Integer chefId, @Param("currentTime") LocalTime currentTime);

       @EntityGraph(attributePaths = {
          "slots",
          "slots.recipe",
          "slots.recipe.components",
          "slots.recipe.components.product",
          "chef"
       })
       @Query("SELECT wp FROM WeeklyPlan wp WHERE wp.status IN ('ACTIVE', 'IN_PROGRESS') AND wp.weekEndDate < :today")
       List<WeeklyPlan> findExpiredActivePlans(@Param("today") LocalDate today);

    @Query("SELECT rc.product.id, SUM((rc.quantity / r.portions) * s.quantity) " +
           "FROM WeeklyPlan wp " +
           "JOIN wp.slots s " +
           "JOIN s.recipe r " +
           "JOIN r.components rc " +
           "WHERE wp.status IN ('ACTIVE', 'IN_PROGRESS') AND s.status IN ('PENDING', 'IN_PROGRESS') " +
           "AND (:excludePlanId IS NULL OR wp.id != :excludePlanId) " +
           "GROUP BY rc.product.id")
    List<Object[]> calculateReservedStock(@Param("excludePlanId") Long excludePlanId);

    @Query("SELECT rc.product.id, SUM((rc.quantity / r.portions) * s.quantity) " +
           "FROM WeeklyPlanSlot s " +
           "JOIN s.recipe r " +
           "JOIN r.components rc " +
           "WHERE s.weeklyPlan.id = :planId AND s.status IN ('PENDING', 'IN_PROGRESS') " +
           "GROUP BY rc.product.id")
    List<Object[]> calculateRequiredStockForPlan(@Param("planId") Long planId);

       @EntityGraph(attributePaths = {
                     "slots",
                     "slots.recipe",
                     "slots.recipe.components",
                     "slots.recipe.components.product",
                     "slots.confirmedBy",
                     "slots.students",
                     "slots.students.student",
                     "slots.students.cancelledBy",
                     "chef"
       })
    Optional<WeeklyPlan> findWithDetailsById(Long id);

       @EntityGraph(attributePaths = {"slots", "slots.recipe", "slots.confirmedBy", "slots.students", "slots.students.student", "slots.students.cancelledBy", "chef"})
    Page<WeeklyPlan> findAllByChefId(Integer chefId, Pageable pageable);

       @EntityGraph(attributePaths = {"slots", "slots.recipe", "slots.confirmedBy", "slots.students", "slots.students.student", "slots.students.cancelledBy", "chef"})
    Page<WeeklyPlan> findAll(@NonNull Pageable pageable);

       @EntityGraph(attributePaths = {"slots", "slots.recipe", "slots.confirmedBy", "slots.students", "slots.students.student", "slots.students.cancelledBy", "chef"})
    Optional<WeeklyPlan> findByChefIdAndWeekStartDateAndStatus(Integer chefId, LocalDate weekStartDate, WeeklyPlanStatus status);

       @EntityGraph(attributePaths = {"slots", "slots.recipe", "slots.confirmedBy", "slots.students", "slots.students.student", "slots.students.cancelledBy", "chef"})
    Optional<WeeklyPlan> findByChefIdAndWeekStartDateAndStatusIn(Integer chefId, LocalDate weekStartDate, List<WeeklyPlanStatus> statuses);
}
