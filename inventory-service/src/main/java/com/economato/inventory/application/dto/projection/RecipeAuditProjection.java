package com.economato.inventory.application.dto.projection;

import java.time.LocalDateTime;

public interface RecipeAuditProjection {
    Integer getId();

    String getAction();

    String getDetails();

    String getPreviousState();

    String getNewState();

    LocalDateTime getAuditDate();

    RecipeInfo getRecipe();

    UserInfo getUser();

    interface RecipeInfo {
        Integer getId();
    }

    interface UserInfo {
        Integer getId();
    }
}
