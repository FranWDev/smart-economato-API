package com.economato.inventory.infrastructure.aspect.shared;
import com.economato.inventory.application.usecase.recipe.RecipeDraftService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.infrastructure.aspect.order.OrderAuditAspect;
import com.economato.inventory.infrastructure.aspect.product.ProductAuditAspect;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.economato.inventory.application.dto.shared.event.RealtimeSyncEvent;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeComponentResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderDetailResponseDTO;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aspecto que intercepta métodos anotados con {@link RealtimeSync} y emite un evento
 * de sincronización al topic WebSocket {@code /topic/sync} tras la ejecución exitosa.
 *
 * <p>Sigue el mismo patrón que {@code OrderAuditAspect} y {@code ProductAuditAspect}:
 * <ul>
 *   <li>Activo en todos los perfiles excepto {@code test} (y en {@code websocket-test} para pruebas del aspecto).</li>
 *   <li>Nunca propaga excepciones del broadcast: si falla el WebSocket, la operación principal
 *       ya fue completada y su resultado se devuelve igualmente.</li>
 *   <li>Se ejecuta DESPUÉS del {@code proceed()}, nunca antes.</li>
 * </ul>
 *
 * <h3>Estrategia de extracción del entityId:</h3>
 * <ol>
 *   <li>Si {@code idFromArg >= 0}: se usa el argumento en esa posición.</li>
 *   <li>Si {@code idFromArg == -1} (default): se intenta llamar a {@code getId()} en el resultado.</li>
 *   <li>Si {@code idFromArg == -2} o no se puede extraer: {@code entityId = null} (operación masiva).</li>
 * </ol>
 *
 * <h3>Estrategia de extracción de entityIds (batch):</h3>
 * <ul>
 *   <li>{@code idsFromResult = "none"} (default): {@code entityIds} queda vacío.</li>
 *   <li>{@code idsFromResult = "productIds"}: extrae {@code product.id} de {@code StockLedger}
 *       o {@code List<StockLedger>}, deduplicado.</li>
 * </ul>
 *
 * <h3>Prevención de doble emisión:</h3>
 * <p>Si un método anotado llama internamente a otro método anotado, el aspecto detectará
 * la llamada anidada mediante un flag {@code ThreadLocal} y omitirá la emisión del evento
 * interno, garantizando que solo se emita un evento por cada operación de negocio.
 */
@Aspect
@Component
@Profile({ "!test", "websocket-test" })
@Slf4j
@RequiredArgsConstructor
public class RealtimeSyncAspect {

    private final WebSocketNotificationService webSocketNotificationService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Flag de re-entrada: detecta si ya estamos dentro de un método @RealtimeSync.
     * Previene la doble emisión cuando un servicio anotado llama a otro servicio anotado
     * (por ejemplo, RecipeDraftService.approveDraft() → RecipeService.save()).
     */
    private static final ThreadLocal<Boolean> IN_SYNC = ThreadLocal.withInitial(() -> false);

    @Around("@annotation(sync)")
    public Object aroundSync(ProceedingJoinPoint joinPoint, RealtimeSync sync) throws Throwable {
        boolean isOutermost = !IN_SYNC.get();

        if (isOutermost) {
            IN_SYNC.set(true);
        }

        try {
            Object result = joinPoint.proceed();

            // Solo el método anotado más externo en la cadena de llamadas emite el evento.
            if (isOutermost) {
                emitEvent(joinPoint, sync, result);
            } else {
                log.debug("Skipping nested sync event for entityType={}, action={} (outer event already scheduled)",
                        sync.entityType(), sync.action());
            }

            return result;

        } finally {
            // Limpiar SIEMPRE, aunque se lance una excepción, para no dejar el flag corrupto.
            if (isOutermost) {
                IN_SYNC.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private void emitEvent(ProceedingJoinPoint joinPoint, RealtimeSync sync, Object result) {
        try {
            Object entityId = resolveEntityId(joinPoint, sync, result);
            List<Object> entityIds = new ArrayList<>(resolveEntityIds(sync, result));

            // Mantener la lista vacía cuando la estrategia es none; solo añadir el ID principal
            // cuando ya existe una lista derivada del resultado.
            if (entityId != null && !entityIds.isEmpty() && !entityIds.contains(entityId)) {
                entityIds.add(entityId);
            }

            String changedBy = resolveChangedBy();
            List<String> affectedDomains = Arrays.asList(sync.affectedDomains());

            RealtimeSyncEvent event = RealtimeSyncEvent.builder()
                    .entityType(sync.entityType())
                    .entityId(entityId)
                    .entityIds(entityIds)
                    .action(sync.action())
                    .affectedDomains(affectedDomains)
                    .changedBy(changedBy)
                    .timestamp(LocalDateTime.now())
                    .build();

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                log.debug("Transaction active. Registering sync event for afterCommit: entityType={}, action={}",
                        sync.entityType(), sync.action());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        webSocketNotificationService.broadcastSync(event);
                    }
                });
            } else {
                log.debug("No active transaction. Emitting sync event immediately: entityType={}, action={}",
                        sync.entityType(), sync.action());
                webSocketNotificationService.broadcastSync(event);
            }

        } catch (Exception e) {
            // Nunca propagar: el resultado del método principal ya fue obtenido.
            log.error("Error building or sending RealtimeSyncEvent for entityType={}, action={}: {}",
                    sync.entityType(), sync.action(), e.getMessage(), e);
        }
    }

    private Object resolveEntityId(ProceedingJoinPoint joinPoint, RealtimeSync sync, Object result) {
        int idFromArg = sync.idFromArg();

        // -2 → operación masiva, entityId siempre null
        if (idFromArg == -2) {
            return null;
        }

        // Arg explícito
        if (idFromArg >= 0) {
            Object[] args = joinPoint.getArgs();
            if (args != null && idFromArg < args.length) {
                return args[idFromArg];
            }
            return null;
        }

        // Default (-1): intentar extraer del resultado mediante getId()
        return extractIdFromResult(result);
    }

    /**
     * Extrae la lista de IDs según la estrategia {@code idsFromResult} de la anotación.
     * Estrategias soportadas:
     * <ul>
     *   <li>"none" → lista vacía (default)</li>
     *   <li>"productIds" → IDs de producto extraídos de {@code StockLedger} o {@code List<StockLedger>}</li>
     *   <li>"orderProductIds" → IDs de producto extraídos de {@code OrderResponseDTO.details[].productId}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveEntityIds(RealtimeSync sync, Object result) {
        String strategy = sync.idsFromResult();

        if ("none".equals(strategy) || result == null) {
            return Collections.emptyList();
        }

        Object unwrappedResult = result;
        if (result instanceof java.util.Optional<?> opt) {
            if (opt.isEmpty()) return Collections.emptyList();
            unwrappedResult = opt.get();
        }

        if ("productIds".equals(strategy)) {
            // Caso 1: el resultado es una List<StockLedger>
            if (unwrappedResult instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof StockLedger)
                        .map(item -> (StockLedger) item)
                        .map(ledger -> ledger.getProduct() != null
                                ? (Object) ledger.getProduct().getId()
                                : null)
                        .filter(id -> id != null)
                        .distinct()
                        .collect(Collectors.toList());
            }

            // Caso 2: el resultado es un StockLedger único
            if (unwrappedResult instanceof StockLedger ledger) {
                if (ledger.getProduct() != null && ledger.getProduct().getId() != null) {
                    return List.of(ledger.getProduct().getId());
                }
            }
        }

        if ("orderProductIds".equals(strategy)) {
            // OrderResponseDTO.details[].productId
            if (unwrappedResult instanceof OrderResponseDTO orderDTO) {
                if (orderDTO.getDetails() != null) {
                    return orderDTO.getDetails().stream()
                            .map(OrderDetailResponseDTO::getProductId)
                            .filter(id -> id != null)
                            .map(id -> (Object) id)
                            .distinct()
                            .collect(Collectors.toList());
                }
            }
        }

        if ("recipeProductIds".equals(strategy)) {
            // RecipeResponseDTO.components[].productId
            if (unwrappedResult instanceof RecipeResponseDTO recipeDTO) {
                if (recipeDTO.getComponents() != null) {
                    return recipeDTO.getComponents().stream()
                            .map(RecipeComponentResponseDTO::getProductId)
                            .filter(id -> id != null)
                            .map(id -> (Object) id)
                            .distinct()
                            .collect(Collectors.toList());
                }
            }
            // También soportar si el resultado es una lista de IDs directamente (para métodos void que retornan IDs)
            if (unwrappedResult instanceof List<?> list) {
                return list.stream()
                        .filter(id -> id instanceof Integer || id instanceof Long)
                        .distinct()
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    private Object extractIdFromResult(Object result) {
        if (result == null) {
            return null;
        }

        // Si es Optional, intentar desenvolverlo
        if (result instanceof java.util.Optional<?> opt) {
            if (opt.isEmpty()) return null;
            return extractIdViaReflection(opt.get());
        }

        return extractIdViaReflection(result);
    }

    private Object extractIdViaReflection(Object obj) {
        if (obj == null) return null;
        try {
            Method getId = obj.getClass().getMethod("getId");
            return getId.invoke(obj);
        } catch (NoSuchMethodException e) {
            // Resultado no tiene getId(), entityId será null
            return null;
        } catch (Exception e) {
            log.debug("Could not extract entityId via getId() from {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String resolveChangedBy() {
        try {
            User user = securityContextHelper.getCurrentUser();
            return user != null ? user.getName() : "Sistema";
        } catch (Exception e) {
            return "Sistema";
        }
    }
}
