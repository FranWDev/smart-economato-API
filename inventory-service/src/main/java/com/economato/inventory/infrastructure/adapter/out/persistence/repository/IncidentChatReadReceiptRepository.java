package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.IncidentChatReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentChatReadReceiptRepository extends JpaRepository<IncidentChatReadReceipt, Long> {
    Optional<IncidentChatReadReceipt> findByIncidentIdAndUserId(Long incidentId, Integer userId);
    List<IncidentChatReadReceipt> findByIncidentId(Long incidentId);
}
