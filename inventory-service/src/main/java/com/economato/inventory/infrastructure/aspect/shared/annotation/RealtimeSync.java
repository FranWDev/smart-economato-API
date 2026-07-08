package com.economato.inventory.infrastructure.aspect.shared.annotation;
import com.economato.inventory.application.dto.order.response.OrderResponseDTO;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.infrastructure.aspect.shared.RealtimeSyncAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de servicio como fuente de eventos de sincronización en tiempo real.
 * El aspecto {@code RealtimeSyncAspect} interceptará el método y emitirá un evento
 * WebSocket al topic {@code /topic/sync} tras una ejecución exitosa.
 *
 * <p>El frontend utiliza el campo {@code affectedDomains} del evento para invalidar
 * su cache local y re-fetchar los datos afectados, sin mostrar ninguna notificación visual.
 *
 * <p>Ejemplo de uso:
 * <pre>{@code
 * @RealtimeSync(
 *     entityType      = "ledger",
 *     action          = "CREATE",
 *     affectedDomains = {"ledger", "product", "weekly_plan", "stock_alerts"},
 *     idsFromResult   = "productIds"   // extrae IDs de producto de List<StockLedger>
 * )
 * public List<StockLedger> processBatchMovements(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RealtimeSync {

    /**
     * Nombre de la entidad principal afectada (ej. {@code "product"}, {@code "weekly_plan"}).
     */
    String entityType();

    /**
     * Acción semántica que describe la operación.
     * Valores típicos: CREATE, UPDATE, DELETE, STATUS_CHANGE, CONFIRM, REVERT, RECEIVE.
     */
    String action();

    /**
     * Dominios de cache que deben invalidarse en el frontend.
     * Valores posibles: product, supplier, recipe, order, weekly_plan, ledger,
     * batch, stock_alerts, user, config.
     */
    String[] affectedDomains();

    /**
     * Índice basado en 0 del argumento que contiene el ID de la entidad primaria.
     * Usar -1 (valor por defecto) para extraer el ID del valor de retorno mediante reflexión (getId()).
     * Usar -2 para indicar que el entityId es null (operaciones masivas o sin ID concreto).
     */
    int idFromArg() default -1;

    /**
     * Estrategia de extracción de {@code entityIds} (array de IDs) para operaciones batch.
     * <ul>
     *   <li>{@code "none"} (por defecto): no se extrae ninguna lista; {@code entityIds} queda vacío.</li>
     *   <li>{@code "productIds"}: el resultado es un {@code StockLedger} o {@code List<StockLedger>};
     *       se extrae el {@code productId} de cada elemento de forma deduplicada.</li>
     *   <li>{@code "orderProductIds"}: el resultado es un {@code OrderResponseDTO};
     *       se extraen los {@code productId} de {@code details[].productId} de forma deduplicada.
     *       Permite al frontend hacer re-fetch quirúrgico de solo los N productos recibidos en la orden,
     *       en lugar de invalidar todo el dominio {@code product}.</li>
     * </ul>
     * Cuando {@code entityIds} no está vacío, el frontend puede hacer re-fetch quirúrgico
     * de las entidades concretas en lugar de invalidar todo el dominio.
     */
    String idsFromResult() default "none";
}
