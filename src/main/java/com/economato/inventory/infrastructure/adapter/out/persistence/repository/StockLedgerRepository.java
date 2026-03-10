package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.model.StockLedger;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id = :productId ORDER BY l.sequenceNumber ASC")
    List<StockLedger> findByProductIdOrderBySequenceNumber(@Param("productId") Integer productId);

    @EntityGraph(attributePaths = { "product", "user" })
    Page<StockLedger> findByProductId(@Param("productId") Integer productId, Pageable pageable);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id = :productId ORDER BY l.sequenceNumber DESC LIMIT 1")
    Optional<StockLedger> findLastTransactionByProductId(@Param("productId") Integer productId);

    long countByProductId(Integer productId);

    boolean existsByCurrentHash(String currentHash);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.verified = false")
    List<StockLedger> findByVerifiedFalse();

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id = :productId AND l.sequenceNumber BETWEEN :startSeq AND :endSeq ORDER BY l.sequenceNumber ASC")
    List<StockLedger> findByProductIdAndSequenceRange(
            @Param("productId") Integer productId,
            @Param("startSeq") Long startSeq,
            @Param("endSeq") Long endSeq);

    @Query("SELECT CAST(l.transactionTimestamp AS date), SUM(ABS(l.quantityDelta)) FROM StockLedger l " +
           "WHERE l.product.id = :productId " +
           "AND l.transactionTimestamp BETWEEN :startDate AND :endDate " +
           "AND (l.movementType = 'SALIDA' OR (l.movementType = 'AJUSTE' AND l.quantityDelta < 0)) " +
           "GROUP BY CAST(l.transactionTimestamp AS date) " +
           "ORDER BY CAST(l.transactionTimestamp AS date) ASC")
    List<Object[]> getConsumptionByProductIdAndDateRange(
            @Param("productId") Integer productId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    @Modifying
    @Transactional
    void deleteAllByProductId(Integer productId);
}
