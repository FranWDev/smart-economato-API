package com.economatom.inventory.repository;

import com.economatom.inventory.model.StockLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    @Query("SELECT l FROM StockLedger l WHERE l.product.id = :productId ORDER BY l.sequenceNumber ASC")
    List<StockLedger> findByProductIdOrderBySequenceNumber(@Param("productId") Integer productId);

    @Query("SELECT l FROM StockLedger l WHERE l.product.id = :productId ORDER BY l.sequenceNumber DESC LIMIT 1")
    Optional<StockLedger> findLastTransactionByProductId(@Param("productId") Integer productId);

    long countByProductId(Integer productId);

    boolean existsByCurrentHash(String currentHash);

    List<StockLedger> findByVerifiedFalse();

    @Query("SELECT l FROM StockLedger l WHERE l.product.id = :productId AND l.sequenceNumber BETWEEN :startSeq AND :endSeq ORDER BY l.sequenceNumber ASC")
    List<StockLedger> findByProductIdAndSequenceRange(
            @Param("productId") Integer productId,
            @Param("startSeq") Long startSeq,
            @Param("endSeq") Long endSeq);

    @Modifying
    @Transactional
    void deleteAllByProductId(Integer productId);
}
