package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockWeeklyConsumptionHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockWeeklyConsumptionHistoryRepository extends JpaRepository<StockWeeklyConsumptionHistory, Integer> {

    @EntityGraph(attributePaths = { "product", "weeklyConsumption" })
    Optional<StockWeeklyConsumptionHistory> findOneById(Integer id);
}
