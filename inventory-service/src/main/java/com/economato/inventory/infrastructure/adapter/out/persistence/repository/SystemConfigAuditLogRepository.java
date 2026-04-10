package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.SystemConfigAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigAuditLogRepository extends JpaRepository<SystemConfigAuditLog, Long> {

    Page<SystemConfigAuditLog> findByCategoryOrderByChangedAtDesc(String category, Pageable pageable);

    Page<SystemConfigAuditLog> findAllByOrderByChangedAtDesc(Pageable pageable);
}
