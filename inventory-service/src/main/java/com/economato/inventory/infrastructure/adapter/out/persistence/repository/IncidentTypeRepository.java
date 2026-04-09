package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentTypeRepository extends JpaRepository<IncidentType, Integer> {
    Optional<IncidentType> findByNameIgnoreCase(String name);

    List<IncidentType> findByIsActiveTrue();
}
