package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.ProductBatch;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long>, JpaSpecificationExecutor<ProductBatch> {

    @Override
    @EntityGraph(attributePaths = {"product"})
    Optional<ProductBatch> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"product"})
    Page<ProductBatch> findAll(Specification<ProductBatch> spec, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteAllByProductId(Integer productId);

    @Query("SELECT b FROM ProductBatch b WHERE b.ledgerTransaction.id = :id")
    List<ProductBatch> findByLedgerTransactionId(@Param("id") Long id);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.product.id = :productId AND b.depleted = false " +
            "ORDER BY b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findActiveByProductIdOrderByExpiration(@Param("productId") Integer productId);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.depleted = false " +
            "ORDER BY b.product.id ASC, b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findAllActiveBatchesOrderByExpiration();

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
            "ORDER BY b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findByProductIdAndDepletedFalseOrderByExpirationDateAsc(@Param("productId") Integer productId);

    @Query("SELECT b FROM ProductBatch b JOIN FETCH b.product " +
            "WHERE b.product.id = :productId " +
            "ORDER BY b.expirationDate ASC, b.receivedAt ASC")
    List<ProductBatch> findByProductIdOrderByExpirationDateAsc(@Param("productId") Integer productId);

    @Query("SELECT COALESCE(SUM(b.remainingQuantity), 0) FROM ProductBatch b " +
            "WHERE b.product.id = :productId AND b.depleted = false")
    java.math.BigDecimal sumRemainingQuantityByProductId(@Param("productId") Integer productId);
}
