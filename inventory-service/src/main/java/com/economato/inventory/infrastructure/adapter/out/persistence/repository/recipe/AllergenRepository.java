package com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.application.dto.recipe.projection.AllergenProjection;
import com.economato.inventory.domain.model.recipe.Allergen;

import java.util.List;
import java.util.Optional;

public interface AllergenRepository extends JpaRepository<Allergen, Integer> {

    List<Allergen> findByNameContainingIgnoreCase(String namePart);

    Page<AllergenProjection> findAllProjectedBy(Pageable pageable);

    Optional<AllergenProjection> findProjectedById(Integer id);

    List<AllergenProjection> findProjectedByNameContainingIgnoreCase(String namePart);

    Optional<AllergenProjection> findProjectedByNameIgnoreCase(String name);
}
