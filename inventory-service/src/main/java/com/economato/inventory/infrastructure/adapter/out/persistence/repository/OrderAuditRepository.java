package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.OrderAuditProjection;
import com.economato.inventory.domain.model.OrderAudit;

import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface OrderAuditRepository extends JpaRepository<OrderAudit, Integer> {

       @EntityGraph(attributePaths = {"user", "order"})
       Optional<OrderAudit> findById(Integer id);

       List<OrderAudit> findByOrderId(Integer id);

       List<OrderAudit> findByUserId(Integer id);

       List<OrderAudit> findByAuditDateBetween(LocalDateTime start, LocalDateTime end);

       @Query("SELECT oa FROM OrderAudit oa " +
                     "LEFT JOIN FETCH oa.order " +
                     "LEFT JOIN FETCH oa.user " +
                     "WHERE oa.order.id = :orderId")
       List<OrderAudit> findByOrderIdWithDetails(@Param("orderId") Integer orderId);

       @Query("SELECT oa FROM OrderAudit oa " +
                     "LEFT JOIN FETCH oa.order " +
                     "LEFT JOIN FETCH oa.user " +
                     "WHERE oa.auditDate BETWEEN :start AND :end")
       List<OrderAudit> findByAuditDateBetweenWithDetails(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    Page<OrderAuditProjection> findAllProjectedBy(Pageable pageable);

    List<OrderAuditProjection> findProjectedByOrderId(Integer orderId);

    List<OrderAuditProjection> findProjectedByUserId(Integer userId);

    List<OrderAuditProjection> findProjectedByAuditDateBetween(LocalDateTime start, LocalDateTime end);
}
