package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.LedgerBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerBlockRepository extends org.springframework.data.repository.Repository<LedgerBlock, Long> {

    LedgerBlock save(LedgerBlock block);

    Optional<LedgerBlock> findTopByOrderByBlockNumberDesc();

    Optional<LedgerBlock> findByBlockNumber(Long blockNumber);

    List<LedgerBlock> findAllByOrderByBlockNumberAsc();

    Page<LedgerBlock> findAll(Pageable pageable);

    long count();
}
