package com.economato.inventory.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alerta predictiva de stock bajo para un producto concreto.
 * Combina la proyección Holt-Winters de consumo futuro con el stock
 * actual y los pedidos activos (CREATED / PENDING / REVIEW).
 */
@Getter
@Builder
public class StockAlertDTO {

    /** ID del producto. */
    private Integer productId;

    /** Nombre del producto. */
    private String productName;

    /** Unidad de medida (kg, L, ud., …). */
    private String unit;

    /** Stock físico actual según `product.currentStock`. */
    private BigDecimal currentStock;

    /**
     * Suma de cantidades solicitadas en pedidos con estado
     * CREATED, PENDING o REVIEW (stock "en tránsito").
     * Cero si no hay pedidos activos.
     */
    private BigDecimal pendingOrderQuantity;

    /**
     * Consumo proyectado para los próximos {@code horizonDays} días
     * (por defecto 14) calculado con Holt-Winters Triple Exponential Smoothing.
     */
    private BigDecimal projectedConsumption;

    /**
     * Gap = proyección - (stock + pendientes).
     * Positivo → falta stock; negativo → hay margen.
     */
    private BigDecimal effectiveGap;

    /**
     * Días estimados que cubre el stock efectivo (actual + pedidos).
     * Calculado como {@code (currentStock + pending) / consumoDiarioProyectado}.
     */
    private int estimatedDaysRemaining;

    /** Nivel de urgencia basado en {@code estimatedDaysRemaining}. */
    private AlertSeverity severity;

    /**
     * Indica si el déficit es cubierto, parcialmente cubierto o
     * no cubierto por pedidos en tránsito.
     */
    private AlertResolution resolution;

    /**
     * Mensaje legible en español para el administrador.
     * Ejemplo: "Stock insuficiente. El pedido activo no cubre la demanda
     * proyectada — faltan ~2,4 kg para la próxima semana."
     */
    private String message;

    /**
     * Las recetas que más consumen este producto, ordenadas por consumo
     * proyectado descendente. Útil para que el administrador entienda
     * el origen del consumo.
     */
    private List<String> topConsumingRecipes;
}
