package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.ProductBatch;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteAllByProductId(Integer productId);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.product.id = :productId AND b.depleted = false " +
            "ORDER BY CASE WHEN b.expirationDate IS NULL THEN 1 ELSE 0 END, b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findActiveByProductIdOrderByExpiration(@Param("productId") Integer productId);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.depleted = false AND b.expirationDate IS NOT NULL AND b.expirationDate <= :date " +
            "ORDER BY b.expirationDate ASC")
    List<ProductBatch> findExpiringBefore(@Param("date") LocalDate date);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.depleted = false AND b.expirationDate IS NOT NULL AND b.expirationDate < CURRENT_DATE " +
            "ORDER BY b.expirationDate ASC")
    List<ProductBatch> findExpiredWithRemainingStock();

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.product.id = :productId AND b.depleted = false " +
            "ORDER BY CASE WHEN b.expirationDate IS NULL THEN 1 ELSE 0 END, b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findByProductIdAndDepletedFalseOrderByExpirationDateAsc(@Param("productId") Integer productId);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.product.id = :productId " +
            "ORDER BY CASE WHEN b.expirationDate IS NULL THEN 1 ELSE 0 END, b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findByProductIdOrderByExpirationDateAsc(@Param("productId") Integer productId);

    @Query("SELECT COALESCE(SUM(b.remainingQuantity), 0) FROM ProductBatch b " +
            "WHERE b.product.id = :productId AND b.depleted = false")
    java.math.BigDecimal sumRemainingQuantityByProductId(@Param("productId") Integer productId);
}
