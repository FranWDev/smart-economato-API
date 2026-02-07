package com.economatom.inventory.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para eventos de auditoría de cocinado de recetas enviados a Kafka.
 * No contiene referencias a entidades JPA para ser serializable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCookingAuditEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer recipeId;
    private String recipeName;
    private Integer userId;
    private String userName;
    private BigDecimal quantityCooked;
    private String details;
    private String componentsState;
    private LocalDateTime cookingDate;
}
