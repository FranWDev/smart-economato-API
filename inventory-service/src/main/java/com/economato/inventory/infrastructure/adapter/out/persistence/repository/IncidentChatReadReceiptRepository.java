package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.IncidentChatReadReceipt;

public interface IncidentChatReadReceiptRepository extends JpaRepository<IncidentChatReadReceipt, Long> {
    @EntityGraph(attributePaths = {"user"})
    Optional<IncidentChatReadReceipt> findByIncidentIdAndUserId(Long incidentId, Integer userId);

    @EntityGraph(attributePaths = {"user"})
    List<IncidentChatReadReceipt> findByIncidentId(Long incidentId);
}
