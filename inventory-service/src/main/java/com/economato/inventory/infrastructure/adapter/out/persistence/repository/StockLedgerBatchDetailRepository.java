package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface StockLedgerBatchDetailRepository extends JpaRepository<StockLedgerBatchDetail, Long> {
    List<StockLedgerBatchDetail> findByLedgerTransactionId(Long ledgerTransactionId);

    @Query("SELECT d FROM StockLedgerBatchDetail d JOIN FETCH d.batch b LEFT JOIN FETCH b.product WHERE d.ledgerTransaction.id IN :txIds")
    List<StockLedgerBatchDetail> findByLedgerTransactionIdIn(@Param("txIds") Collection<Long> txIds);

    List<StockLedgerBatchDetail> findByBatchId(Long batchId);

    @Query("SELECT d FROM StockLedgerBatchDetail d JOIN FETCH d.ledgerTransaction WHERE d.batch.id IN :batchIds")
    List<StockLedgerBatchDetail> findByBatchIdIn(@Param("batchIds") Collection<Long> batchIds);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM StockLedgerBatchDetail d WHERE d.ledgerTransaction.product.id = :productId")
    void deleteAllByProductId(@Param("productId") Integer productId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM StockLedgerBatchDetail d WHERE d.batch.id = :batchId")
    void deleteAllByBatchId(@Param("batchId") Long batchId);
}
