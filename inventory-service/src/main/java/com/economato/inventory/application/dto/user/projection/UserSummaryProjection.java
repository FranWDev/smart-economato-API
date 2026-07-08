package com.economato.inventory.application.dto.user.projection;

import com.economato.inventory.domain.model.user.Role;

/**
 * Proyección resumen para evitar cargar datos innecesarios en relaciones
 * recursivas.
 */
public interface UserSummaryProjection {

    Integer getId();

    String getName();

    String getUser();

    Role getRole();
}
