package com.economato.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.OrderAudit;

public interface OrderAuditRepository extends JpaRepository<OrderAudit, Integer> {

       List<OrderAudit> findByOrderId(Integer id);

       List<OrderAudit> findByUsersId(Integer id);

       List<OrderAudit> findByAuditDateBetween(LocalDateTime start, LocalDateTime end);

       @Query("SELECT oa FROM OrderAudit oa " +
                     "LEFT JOIN FETCH oa.order " +
                     "LEFT JOIN FETCH oa.users " +
                     "WHERE oa.order.id = :orderId")
       List<OrderAudit> findByOrderIdWithDetails(@Param("orderId") Integer orderId);

       @Query("SELECT oa FROM OrderAudit oa " +
                     "LEFT JOIN FETCH oa.order " +
                     "LEFT JOIN FETCH oa.users " +
                     "WHERE oa.auditDate BETWEEN :start AND :end")
       List<OrderAudit> findByAuditDateBetweenWithDetails(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

       // --- Proyecciones ---

       @Query("SELECT oa FROM OrderAudit oa")
       org.springframework.data.domain.Page<com.economato.inventory.dto.projection.OrderAuditProjection> findAllProjectedBy(
                     org.springframework.data.domain.Pageable pageable);

       @Query("SELECT oa FROM OrderAudit oa WHERE oa.order.id = :orderId")
       List<com.economato.inventory.dto.projection.OrderAuditProjection> findProjectedByOrderId(
                     @Param("orderId") Integer orderId);

       @Query("SELECT oa FROM OrderAudit oa WHERE oa.users.id = :userId")
       List<com.economato.inventory.dto.projection.OrderAuditProjection> findProjectedByUsersId(
                     @Param("userId") Integer userId);

       @Query("SELECT oa FROM OrderAudit oa WHERE oa.auditDate BETWEEN :start AND :end")
       List<com.economato.inventory.dto.projection.OrderAuditProjection> findProjectedByAuditDateBetween(
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);
}
