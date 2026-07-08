package com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared;

import com.economato.inventory.domain.model.shared.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Integer> {
}
