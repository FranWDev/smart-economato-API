package com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock;

import com.economato.inventory.domain.model.stock.StockPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPredictionRepository extends JpaRepository<StockPrediction, Integer> {

    @EntityGraph(attributePaths = {"product"})
    Page<StockPrediction> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    @org.springframework.data.jpa.repository.Query("SELECT sp FROM StockPrediction sp JOIN sp.product p WHERE p.isHidden = false")
    List<StockPrediction> findAllActive();
}
