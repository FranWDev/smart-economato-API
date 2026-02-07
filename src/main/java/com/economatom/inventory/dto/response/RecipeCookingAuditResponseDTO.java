package com.economatom.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCookingAuditResponseDTO {

    private Long id;
    private Integer recipeId;
    private String recipeName;
    private Integer userId;
    private String userName;
    private BigDecimal quantityCooked;
    private String details;
    private LocalDateTime cookingDate;
}
