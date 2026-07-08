package com.economato.inventory.infrastructure.adapter.out.persistence.repository.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.order.projection.OrderDetailProjection;
import com.economato.inventory.application.dto.product.projection.PendingProductQuantity;
import com.economato.inventory.domain.model.order.OrderDetail;
import com.economato.inventory.domain.model.order.OrderDetailId;

import java.util.List;
import java.util.Optional;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, OrderDetailId> {

        @Query("SELECT od FROM OrderDetail od WHERE od.id.orderId = :orderId")
        List<OrderDetail> findByOrderId(@Param("orderId") Integer orderId);

        @Query("SELECT od FROM OrderDetail od WHERE od.id.productId = :productId")
        List<OrderDetail> findByProductId(@Param("productId") Integer productId);

        Page<OrderDetailProjection> findAllProjectedBy(Pageable pageable);

        Optional<OrderDetailProjection> findProjectedByIdOrderIdAndIdProductId(
                        Integer orderId, Integer productId);

        List<OrderDetailProjection> findProjectedByIdOrderId(Integer orderId);

        List<OrderDetailProjection> findProjectedByIdProductId(Integer productId);

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
