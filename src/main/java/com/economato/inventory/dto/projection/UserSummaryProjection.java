package com.economato.inventory.dto.projection;

import com.economato.inventory.model.Role;

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
