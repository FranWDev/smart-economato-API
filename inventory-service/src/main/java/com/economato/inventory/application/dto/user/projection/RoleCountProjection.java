package com.economato.inventory.application.dto.user.projection;

import com.economato.inventory.domain.model.user.Role;

public interface RoleCountProjection {
    Role getRole();

    Long getCount();
}
