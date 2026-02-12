package com.economato.inventory.dto.projection;

import java.time.LocalDateTime;

public interface RecipeAuditProjection {
    Integer getId();

    String getAction();

    String getDetails();

    LocalDateTime getAuditDate();

    RecipeInfo getRecipe();

    UserInfo getUsers();

    interface RecipeInfo {
        Integer getId();
    }

    interface UserInfo {
        Integer getId();
    }
}
