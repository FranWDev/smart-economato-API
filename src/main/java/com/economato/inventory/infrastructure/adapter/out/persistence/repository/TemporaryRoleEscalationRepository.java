package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.economato.inventory.domain.model.TemporaryRoleEscalation;

import java.util.Optional;

@Repository
public interface TemporaryRoleEscalationRepository extends JpaRepository<TemporaryRoleEscalation, Integer> {

    Optional<TemporaryRoleEscalation> findByUserId(Integer userId);

    void deleteByUserId(Integer userId);
}
