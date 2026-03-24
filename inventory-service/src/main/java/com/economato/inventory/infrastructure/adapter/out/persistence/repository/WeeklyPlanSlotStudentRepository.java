package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StudentSlotStatus;
import com.economato.inventory.domain.model.WeeklyPlanSlotStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyPlanSlotStudentRepository extends JpaRepository<WeeklyPlanSlotStudent, Long> {

    List<WeeklyPlanSlotStudent> findByStudentIdAndStatus(Integer studentId, StudentSlotStatus status);

    @Query("SELECT s.student.id, s.student.name, " +
           "COUNT(s), " +
           "SUM(CASE WHEN s.status = 'CONFIRMED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.status = 'CANCELLED' THEN 1 ELSE 0 END) " +
           "FROM WeeklyPlanSlotStudent s " +
           "WHERE s.slot.weeklyPlan.chef.id = :chefId " +
           "GROUP BY s.student.id, s.student.name")
    Page<Object[]> findStudentMetricsByChefId(@Param("chefId") Integer chefId, Pageable pageable);

    @Query("SELECT s.student.id, s.student.name, " +
           "COUNT(s), " +
           "SUM(CASE WHEN s.status = 'CONFIRMED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.status = 'CANCELLED' THEN 1 ELSE 0 END) " +
           "FROM WeeklyPlanSlotStudent s " +
           "GROUP BY s.student.id, s.student.name")
    Page<Object[]> findAllStudentMetrics(Pageable pageable);
}
