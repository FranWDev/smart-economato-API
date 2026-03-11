package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.AuditOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditOutboxRepository extends JpaRepository<AuditOutbox, Long> {

    List<AuditOutbox> findTop50ByOrderByCreatedAtAsc();
}
