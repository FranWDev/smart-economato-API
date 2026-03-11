package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.OrderDetailProjection;
import com.economato.inventory.application.dto.projection.PendingProductQuantity;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderDetailId;

import java.util.List;
import java.util.Optional;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, OrderDetailId> {

        @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId")
        List<OrderDetail> findByOrderId(@Param("orderId") Integer orderId);

        @Query("SELECT od FROM OrderDetail od WHERE od.id.productId = :productId")
        List<OrderDetail> findByProductId(@Param("productId") Integer productId);

        // --- Proyecciones ---

        @Query("SELECT od FROM OrderDetail od")
        Page<OrderDetailProjection> findAllProjectedBy(Pageable pageable);

        @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId AND od.id.productId = :productId")
        Optional<OrderDetailProjection> findProjectedById(
                        @Param("orderId") Integer orderId, @Param("productId") Integer productId);

        @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId")
        List<OrderDetailProjection> findProjectedByOrderId(@Param("orderId") Integer orderId);

        @Query("SELECT od FROM OrderDetail od WHERE od.id.productId = :productId")
        List<OrderDetailProjection> findProjectedByProductId(@Param("productId") Integer productId);

        /**
         * Suma la cantidad solicitada de cada producto en pedidos activos
         * (CREATED, PENDING o REVIEW) — es decir, stock que está "en tránsito".
         * Se excluyen CONFIRMED (ya en stock), INCOMPLETE y CANCELLED.
         */
        @Query(value = """
                        SELECT od.product_id AS productId,
                               SUM(od.requested_quantity) AS pendingQuantity
                        FROM order_detail od
                        INNER JOIN order_header o ON o.order_id = od.order_id
                        WHERE o.status IN ('CREATED', 'PENDING', 'REVIEW')
                        GROUP BY od.product_id
                        """, nativeQuery = true)
        List<PendingProductQuantity> findPendingQuantityPerProduct();
}
