package com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock;

import com.economato.inventory.domain.model.stock.StockWeeklyConsumptionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockWeeklyConsumptionHistoryRepository extends JpaRepository<StockWeeklyConsumptionHistory, Integer> {

    @Override
    @EntityGraph(attributePaths = { "product" })
    List<StockWeeklyConsumptionHistory> findAll();

    @Override
    @EntityGraph(attributePaths = { "product" })
    Page<StockWeeklyConsumptionHistory> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "product", "weeklyConsumption" })
    Optional<StockWeeklyConsumptionHistory> findOneById(Integer id);
}
