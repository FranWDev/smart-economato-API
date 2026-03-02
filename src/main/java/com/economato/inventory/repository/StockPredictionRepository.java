package com.economato.inventory.repository;

import com.economato.inventory.model.StockPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la persistencia de predicciones de stock.
 */
@Repository
public interface StockPredictionRepository extends JpaRepository<StockPrediction, Integer> {
}
