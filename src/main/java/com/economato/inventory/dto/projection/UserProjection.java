package com.economato.inventory.dto.projection;

import com.economato.inventory.model.Role;

/**
 * Proyección de interfaz para User.
 * Excluye password y relaciones (orders, inventoryMovements).
 */
public interface UserProjection {

    Integer getId();

    String getName();

    String getUser();

    boolean getIsFirstLogin();

    boolean getIsHidden();

    Role getRole();
}
