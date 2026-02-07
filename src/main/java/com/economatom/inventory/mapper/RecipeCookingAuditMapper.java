package com.economatom.inventory.mapper;

import com.economatom.inventory.dto.response.RecipeCookingAuditResponseDTO;
import com.economatom.inventory.model.RecipeCookingAudit;
import org.springframework.stereotype.Component;

@Component
public class RecipeCookingAuditMapper {

    public RecipeCookingAuditResponseDTO toResponseDTO(RecipeCookingAudit audit) {
        if (audit == null) {
            return null;
        }

        return RecipeCookingAuditResponseDTO.builder()
                .id(audit.getId())
                .recipeId(audit.getRecipe() != null ? audit.getRecipe().getId() : null)
                .recipeName(audit.getRecipe() != null ? audit.getRecipe().getName() : null)
                .userId(audit.getUsers() != null ? audit.getUsers().getId() : null)
                .userName(audit.getUsers() != null ? audit.getUsers().getName() : null)
                .quantityCooked(audit.getQuantityCooked())
                .details(audit.getDetails())
                .cookingDate(audit.getCookingDate())
                .build();
    }
}
