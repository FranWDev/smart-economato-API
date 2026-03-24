package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.WeeklyPlanSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyPlanSlotRepository extends JpaRepository<WeeklyPlanSlot, Long> {

    List<WeeklyPlanSlot> findByWeeklyPlanId(Long planId);

    List<WeeklyPlanSlot> findByWeeklyPlanIdAndDayOfWeek(Long planId, Integer dayOfWeek);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"weeklyPlan", "recipe", "recipe.components", "recipe.components.product", "students", "students.student"})
    java.util.Optional<WeeklyPlanSlot> findWithDetailsById(Long id);
}
