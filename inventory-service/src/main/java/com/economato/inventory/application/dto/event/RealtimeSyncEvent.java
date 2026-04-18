package com.economato.inventory.application.dto.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload emitido al topic WebSocket {@code /topic/sync} cada vez que una operación
 * mutante del sistema se completa con éxito.
 *
 * <p>El frontend usa {@code affectedDomains} para saber qué entradas de su cache
 * local invalidar y re-fetchar, sin mostrar notificación alguna al usuario.
 *
 * <p><strong>Regla del frontend:</strong> NO re-fetchar si {@code changedBy} coincide
 * con el username del usuario en sesión (ya dispone del dato fresco por la respuesta HTTP).
 *
 * <h3>Semántica de entityId vs entityIds</h3>
 * <ul>
 *   <li>Si {@code entityId != null} y {@code entityIds} vacío → operación sobre 1 entidad concreta.</li>
 *   <li>Si {@code entityId == null} y {@code entityIds} no vacío → operación batch; re-fetch sólo esos IDs.</li>
 *   <li>Si {@code entityId == null} y {@code entityIds} vacío → operación masiva; invalidar todo el dominio.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeSyncEvent {

    /**
     * Nombre de la entidad principal (ej. "product", "weekly_plan", "order").
     */
    private String entityType;

    /**
     * ID de la entidad afectada. Null para operaciones batch o masivas.
     */
    private Object entityId;

    /**
     * IDs concretos de las entidades afectadas en operaciones batch.
     * Por ejemplo, en un batch de movimientos de stock sobre 5 productos: [12, 34, 56, 78, 90].
     * Vacío cuando la operación afecta a una sola entidad (usar entityId) o cuando no se conocen
     * los IDs individuales (operación masiva completa).
     */
    @Builder.Default
    private List<Object> entityIds = Collections.emptyList();

    /**
     * Acción semántica: CREATE, UPDATE, DELETE, STATUS_CHANGE, CONFIRM, REVERT, RECEIVE.
     */
    private String action;

    /**
     * Claves de dominio que el frontend debe invalidar en su cache.
     * Valores posibles: product, supplier, recipe, order, weekly_plan, ledger,
     * batch, stock_alerts, user, config.
     */
    private List<String> affectedDomains;

    /**
     * Username del usuario que originó el cambio.
     */
    private String changedBy;

    /**
     * Momento en que se realizó la operación.
     */
    private LocalDateTime timestamp;
}
