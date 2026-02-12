package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.dto.projection.SupplierProjection;
import com.economato.inventory.model.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Integer> {

    List<Supplier> findByNameContainingIgnoreCase(String namePart);

    Optional<Supplier> findByName(String name);

    boolean existsByName(String name);

    // --- Proyecciones ---

    Page<SupplierProjection> findAllProjectedBy(Pageable pageable);

    Optional<SupplierProjection> findProjectedById(Integer id);

    List<SupplierProjection> findProjectedByNameContainingIgnoreCase(String namePart);
}
