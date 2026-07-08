package com.economato.inventory.application.dto.user.projection;
import com.economato.inventory.domain.model.user.User;

import com.economato.inventory.domain.model.user.Role;

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

    UserSummaryProjection getTeacher();
}
