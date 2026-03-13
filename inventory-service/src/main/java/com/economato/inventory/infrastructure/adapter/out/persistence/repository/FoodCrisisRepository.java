package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.FoodCrisis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FoodCrisisRepository extends JpaRepository<FoodCrisis, Long> {
    Optional<FoodCrisis> findByCrisisCode(String crisisCode);
}
