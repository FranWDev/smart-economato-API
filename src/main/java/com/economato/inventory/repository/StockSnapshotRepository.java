package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.economato.inventory.model.StockSnapshot;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, Integer> {

    @Query("SELECT s FROM StockSnapshot s JOIN FETCH s.product WHERE s.productId = :productId")
    Optional<StockSnapshot> findByIdWithProduct(@Param("productId") Integer productId);

    List<StockSnapshot> findByIntegrityStatus(String integrityStatus);

    @Query("SELECT s FROM StockSnapshot s WHERE s.lastVerified IS NULL")
    List<StockSnapshot> findNeverVerified();

    @Query("SELECT COUNT(s) FROM StockSnapshot s")
    long countSnapshots();
}
