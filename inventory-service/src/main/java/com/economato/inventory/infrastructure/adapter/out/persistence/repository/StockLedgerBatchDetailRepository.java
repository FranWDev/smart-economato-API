package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface StockLedgerBatchDetailRepository extends JpaRepository<StockLedgerBatchDetail, Long> {
    List<StockLedgerBatchDetail> findByLedgerTransactionId(Long ledgerTransactionId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM StockLedgerBatchDetail d WHERE d.ledgerTransaction.product.id = :productId")
    void deleteAllByProductId(@Param("productId") Integer productId);
}
