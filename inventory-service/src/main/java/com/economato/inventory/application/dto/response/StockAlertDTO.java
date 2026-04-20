package com.economato.inventory.application.dto.response;

import com.economato.inventory.application.dto.response.AlertType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Alerta predictiva de stock bajo para un producto concreto.
 * Combina la proyección Holt-Winters de consumo futuro con el stock
 * actual y los pedidos activos (CREATED / PENDING / REVIEW).
 */
@Getter
@Builder
public class StockAlertDTO {

    private Integer productId;

    private String productName;

    private String unit;
    private BigDecimal lotQuantity;

    private BigDecimal currentStock;

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

    /** Tipo de alerta: PREDICTION, EXPIRATION o COMBINED. */
    private AlertType alertType;

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

    /** Fecha de caducidad más cercana entre los lotes activos del producto. */
    private LocalDate nearestExpirationDate;

    /** Cantidad total que caduca próximamente para el producto. */
    private BigDecimal expiringQuantity;

    /**
     * Las recetas que más consumen este producto, ordenadas por consumo
     * proyectado descendente. Útil para que el administrador entienda
     * el origen del consumo.
     */
    private List<String> topConsumingRecipes;
}
