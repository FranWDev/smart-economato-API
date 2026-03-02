package com.economato.inventory.dto.response;

/**
 * Nivel de urgencia de una alerta de stock bajo predictivo.
 * Se calcula en función de los días estimados que cubre el stock
 * actual más los pedidos en tránsito frente al consumo proyectado.
 */
public enum AlertSeverity {
    /** ≥ 21 días cubiertos — sin alerta. */
    OK,
    /** 14–21 días cubiertos — aviso preventivo. */
    LOW,
    /** 7–14 días cubiertos — atención recomendada. */
    MEDIUM,
    /** 3–7 días cubiertos — acción necesaria pronto. */
    HIGH,
    /** < 3 días cubiertos — acción urgente. */
    CRITICAL
}
