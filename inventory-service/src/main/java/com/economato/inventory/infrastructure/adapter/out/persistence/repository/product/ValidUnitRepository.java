package com.economato.inventory.infrastructure.adapter.out.persistence.repository.product;

import com.economato.inventory.domain.model.product.ValidUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValidUnitRepository extends JpaRepository<ValidUnit, Integer> {

    Optional<ValidUnit> findByCodeIgnoreCase(String code);

    List<ValidUnit> findByActiveTrueOrderByCodeAsc();

    List<ValidUnit> findAllByOrderByCategoryAscCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
