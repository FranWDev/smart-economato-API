package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPredictionRepository extends JpaRepository<StockPrediction, Integer> {

    @EntityGraph(attributePaths = {"product"})
    Page<StockPrediction> findAll(Pageable pageable);
}
