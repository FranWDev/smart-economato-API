package com.economatom.inventory.repository;

import com.economatom.inventory.model.StockSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, Integer> {

    List<StockSnapshot> findByIntegrityStatus(String integrityStatus);

    @Query("SELECT s FROM StockSnapshot s WHERE s.lastVerified IS NULL")
    List<StockSnapshot> findNeverVerified();

    @Query("SELECT COUNT(s) FROM StockSnapshot s")
    long countSnapshots();
}
