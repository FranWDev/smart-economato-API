package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.StockDailyForecast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockDailyForecastRepository extends JpaRepository<StockDailyForecast, Integer> {

    @Override
    @EntityGraph(attributePaths = { "product" })
    List<StockDailyForecast> findAll();

    @Override
    @EntityGraph(attributePaths = { "product" })
    Page<StockDailyForecast> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "product", "dailyForecast" })
    Optional<StockDailyForecast> findOneById(Integer id);
}
