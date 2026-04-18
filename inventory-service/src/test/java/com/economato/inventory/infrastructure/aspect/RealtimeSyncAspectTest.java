package com.economato.inventory.infrastructure.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.application.dto.event.RealtimeSyncEvent;
import com.economato.inventory.application.usecase.WebSocketNotificationService;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

import org.aspectj.lang.ProceedingJoinPoint;

@ExtendWith(MockitoExtension.class)
class RealtimeSyncAspectTest {

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private RealtimeSyncAspect aspect;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("chef.test");
    }

    // -------------------------------------------------------------------------
    // Test 1: Se llama a broadcastSync tras proceed exitoso
    // -------------------------------------------------------------------------

    @Test
    void aspect_callsBroadcastAfterSuccessfulProceed() throws Throwable {
        RealtimeSync sync = buildAnnotation("product", "UPDATE", new String[]{"product"}, 0, "none");
        when(joinPoint.getArgs()).thenReturn(new Object[]{42});
        when(joinPoint.proceed()).thenReturn(null);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        verify(webSocketNotificationService, times(1)).broadcastSync(any(RealtimeSyncEvent.class));
    }

    // -------------------------------------------------------------------------
    // Test 2: Se extrae entityId del resultado via getId()
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsIdFromResult_whenGetIdExists() throws Throwable {
        RealtimeSync sync = buildAnnotation("recipe", "CREATE", new String[]{"recipe"}, -1, "none");
        ResultWithId result = new ResultWithId(99);
        when(joinPoint.proceed()).thenReturn(result);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(99);
        assertThat(captor.getValue().getEntityIds()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 3: Se extrae entityId del argumento indicado
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsIdFromArg_whenIdFromArgSet() throws Throwable {
        RealtimeSync sync = buildAnnotation("order", "DELETE", new String[]{"order"}, 0, "none");
        when(joinPoint.getArgs()).thenReturn(new Object[]{77});
        when(joinPoint.proceed()).thenReturn(null);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(77);
    }

    // -------------------------------------------------------------------------
    // Test 4: Si broadcastSync lanza, el resultado del método se devuelve igualmente
    // -------------------------------------------------------------------------

    @Test
    void aspect_doesNotPropagateException_whenBroadcastFails() throws Throwable {
        RealtimeSync sync = buildAnnotation("product", "CREATE", new String[]{"product"}, -2, "none");
        ResultWithId result = new ResultWithId(1);
        when(joinPoint.proceed()).thenReturn(result);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);
        org.mockito.Mockito.doThrow(new RuntimeException("WS down"))
                .when(webSocketNotificationService).broadcastSync(any());

        // No debe lanzar excepción
        Object returned = aspect.aroundSync(joinPoint, sync);

        assertThat(returned).isEqualTo(result);
    }

    // -------------------------------------------------------------------------
    // Test 5: Si el método anotado falla, no se llama a broadcast
    // -------------------------------------------------------------------------

    @Test
    void aspect_doesNotCallBroadcast_whenProceedThrows() throws Throwable {
        RealtimeSync sync = buildAnnotation("product", "UPDATE", new String[]{"product", "recipe"}, 0, "none");
        // No hacemos stub de getArgs(): proceed() lanza antes de que el aspecto lo necesite
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> aspect.aroundSync(joinPoint, sync));

        verify(webSocketNotificationService, never()).broadcastSync(any());
    }

    // -------------------------------------------------------------------------
    // Test 6: entityId es null cuando idFromArg = -2 (operación masiva)
    // -------------------------------------------------------------------------

    @Test
    void aspect_entityIdIsNull_whenIdFromArgIsMinusTwo() throws Throwable {
        RealtimeSync sync = buildAnnotation("weekly_plan", "CONFIRM",
                new String[]{"weekly_plan", "ledger", "product", "stock_alerts"}, -2, "none");
        when(joinPoint.proceed()).thenReturn(null);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
        assertThat(captor.getValue().getEntityIds()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 7: El campo changedBy se toma del SecurityContextHelper
    // -------------------------------------------------------------------------

    @Test
    void aspect_setsCurrentUser_inEvent() throws Throwable {
        RealtimeSync sync = buildAnnotation("supplier", "DELETE", new String[]{"supplier"}, 0, "none");
        when(joinPoint.getArgs()).thenReturn(new Object[]{5});
        when(joinPoint.proceed()).thenReturn(null);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isEqualTo("chef.test");
    }

    // -------------------------------------------------------------------------
    // Test 8: Optional resultado es desenvuelto correctamente
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsIdFromOptionalResult() throws Throwable {
        RealtimeSync sync = buildAnnotation("product", "UPDATE", new String[]{"product"}, -1, "none");
        ResultWithId inner = new ResultWithId(55);
        when(joinPoint.proceed()).thenReturn(Optional.of(inner));
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(55);
    }

    // -------------------------------------------------------------------------
    // Test 9: idsFromResult="productIds" extrae IDs de List<StockLedger>
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsProductIds_fromStockLedgerList() throws Throwable {
        RealtimeSync sync = buildAnnotation("ledger", "UPDATE",
                new String[]{"ledger", "product", "weekly_plan"}, -2, "productIds");

        Product p1 = new Product(); p1.setId(10);
        Product p2 = new Product(); p2.setId(20);
        Product p3 = new Product(); p3.setId(10); // duplicado de p1

        StockLedger l1 = StockLedger.builder().product(p1).resultingStock(BigDecimal.TEN).build();
        StockLedger l2 = StockLedger.builder().product(p2).resultingStock(BigDecimal.ONE).build();
        StockLedger l3 = StockLedger.builder().product(p3).resultingStock(BigDecimal.TEN).build();

        when(joinPoint.proceed()).thenReturn(List.of(l1, l2, l3));
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        RealtimeSyncEvent event = captor.getValue();
        // entityId es null porque idFromArg=-2
        assertThat(event.getEntityId()).isNull();
        // entityIds contiene solo los IDs únicos (sin duplicados)
        assertThat(event.getEntityIds()).containsExactlyInAnyOrder(10, 20);
    }

    // -------------------------------------------------------------------------
    // Test 10: idsFromResult="productIds" extrae ID de un StockLedger único
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsProductId_fromSingleStockLedger() throws Throwable {
        RealtimeSync sync = buildAnnotation("ledger", "CREATE",
                new String[]{"ledger", "product"}, -2, "productIds");

        Product p = new Product(); p.setId(42);
        StockLedger ledger = StockLedger.builder().product(p).resultingStock(BigDecimal.TEN).build();

        when(joinPoint.proceed()).thenReturn(ledger);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        assertThat(captor.getValue().getEntityIds()).containsExactly(42);
    }

    // -------------------------------------------------------------------------
    // Test 10b: idsFromResult="orderProductIds" extrae productIds de OrderResponseDTO
    // -------------------------------------------------------------------------

    @Test
    void aspect_extractsOrderProductIds_fromOrderResponseDTO() throws Throwable {
        RealtimeSync sync = buildAnnotation("order", "RECEIVE",
                new String[]{"order", "ledger", "product"}, -2, "orderProductIds");

        // Simular OrderResponseDTO con 3 detalles (productId 10, 20, 10 — duplicado)
        com.economato.inventory.application.dto.response.OrderDetailResponseDTO d1 =
                new com.economato.inventory.application.dto.response.OrderDetailResponseDTO();
        d1.setProductId(10);
        com.economato.inventory.application.dto.response.OrderDetailResponseDTO d2 =
                new com.economato.inventory.application.dto.response.OrderDetailResponseDTO();
        d2.setProductId(20);
        com.economato.inventory.application.dto.response.OrderDetailResponseDTO d3 =
                new com.economato.inventory.application.dto.response.OrderDetailResponseDTO();
        d3.setProductId(10); // duplicado

        com.economato.inventory.application.dto.response.OrderResponseDTO orderDTO =
                new com.economato.inventory.application.dto.response.OrderResponseDTO();
        orderDTO.setDetails(List.of(d1, d2, d3));

        when(joinPoint.proceed()).thenReturn(orderDTO);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, sync);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(webSocketNotificationService).broadcastSync(captor.capture());
        // entityId null porque idFromArg=-2
        assertThat(captor.getValue().getEntityId()).isNull();
        // entityIds contiene solo los IDs únicos (productId 10 y 20, no el duplicado)
        assertThat(captor.getValue().getEntityIds()).containsExactlyInAnyOrder(10, 20);
    }

    // -------------------------------------------------------------------------
    // Test 11: Prevención de doble emisión — el método anidado NO emite evento
    // -------------------------------------------------------------------------

    @Test
    void aspect_preventsDoubleEmission_whenNestedAnnotatedMethodCalled() throws Throwable {
        RealtimeSync outerSync = buildAnnotation("draft", "CREATE", new String[]{"recipe"}, -2, "none");
        RealtimeSync innerSync = buildAnnotation("recipe", "CREATE", new String[]{"recipe"}, -2, "none");

        // JoinPoint separado para la llamada interna (evita recursión infinita)
        ProceedingJoinPoint innerJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(innerJoinPoint.proceed()).thenReturn(null);

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            // Simular llamada anidada al aspecto (como cuando approveDraft() llama a recipeService.save())
            aspect.aroundSync(innerJoinPoint, innerSync);
            return new ResultWithId(1);
        });
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        aspect.aroundSync(joinPoint, outerSync);

        // Solo debe haberse emitido 1 evento (el exterior), no 2
        verify(webSocketNotificationService, times(1)).broadcastSync(any(RealtimeSyncEvent.class));
    }

    // -------------------------------------------------------------------------
    // Test 12: ThreadLocal se limpia tras excepción en proceed()
    // -------------------------------------------------------------------------

    @Test
    void aspect_cleansThreadLocal_afterProceedThrows() throws Throwable {
        RealtimeSync sync = buildAnnotation("product", "UPDATE", new String[]{"product"}, -2, "none");

        // Primera llamada: proceed() lanza excepción
        // Usamos lenient para poder cambiar el stub en la misma prueba
        Mockito.lenient().when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> aspect.aroundSync(joinPoint, sync));

        // Segunda llamada: el ThreadLocal debe haberse limpiado, emite normalmente
        Mockito.reset(joinPoint);
        when(joinPoint.proceed()).thenReturn(null);
        when(securityContextHelper.getCurrentUser()).thenReturn(testUser);
        aspect.aroundSync(joinPoint, sync);

        // Sólo la segunda llamada (exitosa) debe haber emitido 1 evento
        verify(webSocketNotificationService, times(1)).broadcastSync(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RealtimeSync buildAnnotation(String entityType, String action,
                                         String[] affectedDomains, int idFromArg,
                                         String idsFromResult) {
        return new RealtimeSync() {
            @Override public Class<RealtimeSync> annotationType() { return RealtimeSync.class; }
            @Override public String entityType()        { return entityType; }
            @Override public String action()            { return action; }
            @Override public String[] affectedDomains() { return affectedDomains; }
            @Override public int idFromArg()            { return idFromArg; }
            @Override public String idsFromResult()     { return idsFromResult; }
        };
    }

    /** DTO de prueba que expone getId(). */
    static class ResultWithId {
        private final int id;
        ResultWithId(int id) { this.id = id; }
        public int getId()   { return id; }
    }
}
