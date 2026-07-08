package com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.shared.SystemConfigAuditLog;

public interface SystemConfigAuditLogRepository extends JpaRepository<SystemConfigAuditLog, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<SystemConfigAuditLog> findByCategoryOrderByChangedAtDesc(String category, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<SystemConfigAuditLog> findAllByOrderByChangedAtDesc(Pageable pageable);
}
