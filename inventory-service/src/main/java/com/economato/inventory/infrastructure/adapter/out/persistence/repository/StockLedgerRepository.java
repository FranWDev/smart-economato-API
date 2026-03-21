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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.economato.inventory.domain.model.StockLedger;

@Repository
public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.correlationId = :correlationId")
    List<StockLedger> findByCorrelationId(@Param("correlationId") String correlationId);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.orderId = :orderId")
    List<StockLedger> findByOrderId(@Param("orderId") Integer orderId);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id = :productId ORDER BY l.sequenceNumber ASC")
    List<StockLedger> findByProductIdOrderBySequenceNumber(@Param("productId") Integer productId);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id IN :productIds ORDER BY l.product.id ASC, l.sequenceNumber ASC")
    List<StockLedger> findByProductIdInOrderBySequenceNumber(@Param("productIds") Collection<Integer> productIds);

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
           "AND l.quantityDelta < 0 " +
           "GROUP BY CAST(l.transactionTimestamp AS date) " +
           "ORDER BY CAST(l.transactionTimestamp AS date) ASC")
    List<Object[]> getConsumptionByProductIdAndDateRange(
            @Param("productId") Integer productId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT l.product.id, CAST(l.transactionTimestamp AS date), SUM(ABS(l.quantityDelta)) " +
           "FROM StockLedger l WHERE l.product.id IN :productIds " +
           "AND l.transactionTimestamp BETWEEN :startDate AND :endDate " +
           "AND l.quantityDelta < 0 " +
           "GROUP BY l.product.id, CAST(l.transactionTimestamp AS date)")
    List<Object[]> getConsumptionByProductIdsAndDateRange(
            @Param("productIds") List<Integer> productIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteAllByProductId(Integer productId);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id IN :productIds AND l.movementType = 'ENTRADA' AND l.orderId IS NOT NULL AND l.transactionTimestamp BETWEEN :startDate AND :endDate ORDER BY l.transactionTimestamp ASC")
    List<StockLedger> findEntradasWithOrderIdByProductIdsAndDateRange(
            @Param("productIds") List<Integer> productIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id IN :productIds AND l.movementType = 'SALIDA' AND l.transactionTimestamp BETWEEN :startDate AND :endDate ORDER BY l.transactionTimestamp ASC")
    List<StockLedger> findSalidasByProductIdsAndDateRange(
            @Param("productIds") List<Integer> productIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user WHERE l.product.id = :productId AND l.movementType = 'ENTRADA' AND l.transactionTimestamp < :date ORDER BY l.transactionTimestamp DESC LIMIT 1")
    Optional<StockLedger> findLastEntradaBeforeDate(
            @Param("productId") Integer productId,
            @Param("date") LocalDateTime date);

    @Query("SELECT DISTINCT l.product.id FROM StockLedger l ORDER BY l.product.id")
    List<Integer> findDistinctProductIds();

    @Query("SELECT l.product.id, l.currentHash FROM StockLedger l " +
           "WHERE l.product.id IN :productIds " +
           "AND l.sequenceNumber = (SELECT MAX(l2.sequenceNumber) FROM StockLedger l2 WHERE l2.product.id = l.product.id)")
    List<Object[]> findLatestHashesByProductIds(@Param("productIds") List<Integer> productIds);

    @Query("SELECT l.product.id, l FROM StockLedger l JOIN FETCH l.product LEFT JOIN FETCH l.user " +
           "WHERE l.product.id IN :productIds " +
           "AND l.movementType = 'ENTRADA' " +
           "AND l.transactionTimestamp < :date " +
           "AND l.transactionTimestamp = (SELECT MAX(l2.transactionTimestamp) FROM StockLedger l2 " +
           "WHERE l2.product.id = l.product.id AND l2.movementType = 'ENTRADA' " +
           "AND l2.transactionTimestamp < :date)")
    List<Object[]> findLastEntradasBeforeDateBatch(@Param("productIds") Collection<Integer> productIds, @Param("date") LocalDateTime date);
}
