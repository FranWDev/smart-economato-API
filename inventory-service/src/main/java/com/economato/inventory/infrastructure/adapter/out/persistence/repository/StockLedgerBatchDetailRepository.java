package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockLedgerBatchDetailRepository extends JpaRepository<StockLedgerBatchDetail, Long> {
    List<StockLedgerBatchDetail> findByLedgerTransactionId(Long ledgerTransactionId);
}
