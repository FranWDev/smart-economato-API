package com.economato.inventory.dto.projection;

import com.economato.inventory.model.Role;

public interface RoleCountProjection {
    Role getRole();

    Long getCount();
}
