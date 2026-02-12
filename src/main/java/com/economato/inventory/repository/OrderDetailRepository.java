package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.model.OrderDetail;
import com.economato.inventory.model.OrderDetailId;

import java.util.List;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, OrderDetailId> {

    @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId")
    List<OrderDetail> findByOrderId(@Param("orderId") Integer orderId);

    @Query("SELECT od FROM OrderDetail od WHERE od.id.productId = :productId")
    List<OrderDetail> findByProductId(@Param("productId") Integer productId);

    // --- Proyecciones ---

    @Query("SELECT od FROM OrderDetail od")
    org.springframework.data.domain.Page<com.economato.inventory.dto.projection.OrderDetailProjection> findAllProjectedBy(
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId AND od.id.productId = :productId")
    java.util.Optional<com.economato.inventory.dto.projection.OrderDetailProjection> findProjectedById(
            @Param("orderId") Integer orderId, @Param("productId") Integer productId);

    @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId")
    List<com.economato.inventory.dto.projection.OrderDetailProjection> findProjectedByOrderId(
            @Param("orderId") Integer orderId);

    @Query("SELECT od FROM OrderDetail od WHERE od.id.productId = :productId")
    List<com.economato.inventory.dto.projection.OrderDetailProjection> findProjectedByProductId(
            @Param("productId") Integer productId);
}
