package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.WeeklyPlanSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyPlanSlotRepository extends JpaRepository<WeeklyPlanSlot, Long> {

    List<WeeklyPlanSlot> findByWeeklyPlanId(Long planId);

    List<WeeklyPlanSlot> findByWeeklyPlanIdAndDayOfWeek(Long planId, Integer dayOfWeek);

    @EntityGraph(attributePaths = {"weeklyPlan", "recipe", "recipe.components", "recipe.components.product", "students", "students.student"})
    Optional<WeeklyPlanSlot> findWithDetailsById(Long id);

    @Query("SELECT rc.product.id FROM WeeklyPlanSlot s JOIN s.recipe r JOIN r.components rc WHERE s.id = :slotId")
    List<Integer> findProductIdsBySlotId(@Param("slotId") Long slotId);

    Optional<WeeklyPlanSlot> findByCorrelationId(String correlationId);
}
