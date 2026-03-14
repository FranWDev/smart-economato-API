package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.FoodCrisis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface FoodCrisisRepository extends JpaRepository<FoodCrisis, Long> {
    Optional<FoodCrisis> findByCrisisCode(String crisisCode);

    @Query("SELECT f FROM FoodCrisis f WHERE f.status = 'LIFTED' AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR " +
           "LOWER(f.crisisCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.supplier.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.reason) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<FoodCrisis> findHistoryWithSearch(@Param("searchTerm") String searchTerm, Pageable pageable);
}
