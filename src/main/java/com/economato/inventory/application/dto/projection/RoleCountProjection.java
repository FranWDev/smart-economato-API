package com.economato.inventory.application.dto.projection;

import com.economato.inventory.domain.model.Role;

public interface RoleCountProjection {
    Role getRole();

    Long getCount();
}
