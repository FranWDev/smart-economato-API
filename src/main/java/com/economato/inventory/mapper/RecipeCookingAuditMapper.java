package com.economato.inventory.mapper;

import org.springframework.stereotype.Component;

import com.economato.inventory.dto.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.model.RecipeCookingAudit;

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
                .userId(audit.getUser() != null ? audit.getUser().getId() : null)
                .userName(audit.getUser() != null ? audit.getUser().getName() : null)
                .quantityCooked(audit.getQuantityCooked())
                .details(audit.getDetails())
                .cookingDate(audit.getCookingDate())
                .build();
    }
}
