package com.economato.inventory.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO para eventos de auditoría de cocinado de recetas enviados a Kafka.
 * No contiene referencias a entidades JPA para ser serializable.
 *
 * El campo {@code productHistories} contiene el historial de consumo de los
 * últimos 90 días de cada ingrediente de la receta, indexado por productId.
 * Esto evita que el predictor necesite hacer peticiones HTTP al backend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCookingAuditEvent implements Serializable {

    private static final long serialVersionUID = 2L;

    private Integer recipeId;
    private String recipeName;
    private Integer userId;
    private String userName;
    private BigDecimal quantityCooked;
    private String details;
    private String componentsState;
    private LocalDateTime cookingDate;

    /**
     * Historial de consumo de 90 días por producto.
     * Key:   productId
     * Value: lista de entradas diarias [{date, consumed}, ...]
     */
    private Map<Integer, List<DailyConsumption>> productHistories;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyConsumption implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDate date;
        private BigDecimal consumed;
    }
}
