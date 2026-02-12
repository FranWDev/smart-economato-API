package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.dto.projection.AllergenProjection;
import com.economato.inventory.model.Allergen;

import java.util.List;
import java.util.Optional;

public interface AllergenRepository extends JpaRepository<Allergen, Integer> {

    List<Allergen> findByNameContainingIgnoreCase(String namePart);

    // --- Proyecciones ---

    Page<AllergenProjection> findAllProjectedBy(Pageable pageable);

    Optional<AllergenProjection> findProjectedById(Integer id);

    List<AllergenProjection> findProjectedByNameContainingIgnoreCase(String namePart);
}
