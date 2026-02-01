package com.economatom.inventory.repository;

import com.economatom.inventory.model.OrderAudit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
