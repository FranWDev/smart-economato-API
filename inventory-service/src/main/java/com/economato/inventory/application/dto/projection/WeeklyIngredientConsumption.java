package com.economato.inventory.application.dto.projection;

import java.math.BigDecimal;

/**
 * Proyección de resultado de la query nativa que devuelve el consumo
 * semanal de ingredientes agrupado por semana e identificador de producto.
 */
public interface WeeklyIngredientConsumption {

    /**
     * Índice de semana relativo a la fecha de referencia (0 = semana más antigua).
     */
    Integer getWeekIndex();

    Integer getProductId();

    BigDecimal getTotalConsumed();
}
