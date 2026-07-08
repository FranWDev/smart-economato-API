package com.economato.inventory.application.usecase.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.economato.inventory.application.dto.shared.event.RealtimeSyncEvent;
import com.economato.inventory.application.dto.order.response.OrderReviewLockResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderReviewCollaborationStateResponseDTO;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.order.OrderCollaborationFieldLockedException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.order.OrderReviewLockedException;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

@ExtendWith(MockitoExtension.class)
class OrderReviewLockServiceTest {

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private OrderReviewLockService service;

    private final ThreadLocal<User> userContext = new ThreadLocal<>();

    @BeforeEach
    void setUp() {
        when(securityContextHelper.getCurrentUser()).thenAnswer(invocation -> userContext.get());
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).getKey());
        lenient().when(i18nService.getMessage(eq(MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED), anyString()))
                .thenAnswer(invocation -> {
                    String lockedBy = invocation.getArgument(1);
                    return "locked by " + lockedBy;
                });
    }

    @Test
    void acquireLock_concurrentRequests_onlyOneUserAcquiresLock() throws Exception {
        Integer orderId = 101;
        User chefA = buildUser(10, "chef.a", Role.CHEF);
        User chefB = buildUser(11, "chef.b", Role.CHEF);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tasks = List.of(
                () -> tryAcquireWithBarrier(orderId, chefA, start),
                () -> tryAcquireWithBarrier(orderId, chefB, start));

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }

        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof OrderReviewLockedException) {
                    conflictCount++;
                } else {
                    throw ex;
                }
            }
        }

        pool.shutdownNow();

        assertEquals(1, successCount, "Solo un usuario debe adquirir el lock");
        assertEquals(1, conflictCount, "El segundo usuario debe recibir conflicto");
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/sync"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void heartbeatLock_updatesLastSeenAndKeepsOwnership() {
        Integer orderId = 202;
        User chef = buildUser(20, "chef.owner", Role.CHEF);

        userContext.set(chef);
        OrderReviewLockResponseDTO acquired = service.acquireLock(orderId);
        assertTrue(acquired.isLocked());

        OrderReviewLockResponseDTO heartbeat = service.heartbeatLock(orderId);
        assertTrue(heartbeat.isLocked());
        assertTrue(heartbeat.isCurrentUserOwner());
        assertNotNull(heartbeat.getLastSeenAt());
        assertNotNull(heartbeat.getExpiresAt());

        LocalDateTime baseline = acquired.getLastSeenAt() != null ? acquired.getLastSeenAt() : acquired.getAcquiredAt();
        assertNotNull(baseline);
        assertTrue(!heartbeat.getLastSeenAt().isBefore(baseline), "Heartbeat debe refrescar lastSeenAt");
    }

    @Test
    void assertCanTransitionOrder_adminCanConfirmWhileChefOwnsLock() {
        Integer orderId = 303;
        User chef = buildUser(30, "chef.lock", Role.CHEF);
        User admin = buildUser(31, "admin.parallel", Role.ADMIN);

        userContext.set(chef);
        service.acquireLock(orderId);

        userContext.set(admin);
        assertDoesNotThrow(() -> service.assertCanTransitionOrder(orderId, OrderStatus.CONFIRMED));
        assertThrows(OrderReviewLockedException.class,
                () -> service.assertCanTransitionOrder(orderId, OrderStatus.CANCELLED));
    }

    @Test
    void collaborationRequestAndAdmit_supportsUnlimitedCollaborators() {
        Integer orderId = 404;
        User owner = buildUser(40, "chef.owner", Role.CHEF);
        User collaboratorA = buildUser(41, "chef.a", Role.CHEF);
        User collaboratorB = buildUser(42, "chef.b", Role.ELEVATED);

        userContext.set(owner);
        service.acquireLock(orderId);

        userContext.set(collaboratorA);
        service.requestSharedReview(orderId);

        userContext.set(owner);
        OrderReviewCollaborationStateResponseDTO admittedA = service.admitSharedReview(orderId, collaboratorA.getId());
        assertTrue(admittedA.getCollaborators().stream().anyMatch(c -> c.getUserId().equals(collaboratorA.getId())));

        userContext.set(collaboratorB);
        service.requestSharedReview(orderId);

        userContext.set(collaboratorA);
        OrderReviewCollaborationStateResponseDTO admittedB = service.admitSharedReview(orderId, collaboratorB.getId());
        assertTrue(admittedB.getCollaborators().stream().anyMatch(c -> c.getUserId().equals(collaboratorB.getId())));
        assertEquals(3, admittedB.getCollaborators().size());
        assertTrue(admittedB.getPendingRequests().isEmpty());
    }

    @Test
    void lockFieldAndPatch_respectFieldOwnershipAndCollaborationPermission() {
        Integer orderId = 505;
        User owner = buildUser(50, "chef.owner", Role.CHEF);
        User collaborator = buildUser(51, "chef.collab", Role.CHEF);
        User outsider = buildUser(52, "chef.out", Role.CHEF);

        userContext.set(owner);
        service.acquireLock(orderId);

        userContext.set(collaborator);
        service.requestSharedReview(orderId);

        userContext.set(owner);
        service.admitSharedReview(orderId, collaborator.getId());

        userContext.set(collaborator);
        OrderReviewCollaborationStateResponseDTO locked = service.lockField(orderId, "items.0.receivedQuantity");
        assertTrue(locked.getFieldLocks().stream().anyMatch(f -> "items.0.receivedQuantity".equals(f.getFieldPath())));

        userContext.set(owner);
        assertThrows(OrderCollaborationFieldLockedException.class,
            () -> service.patchField(orderId, "items.0.receivedQuantity", 7));

        userContext.set(collaborator);
        OrderReviewCollaborationStateResponseDTO patched = service.patchField(orderId, "items.0.receivedQuantity", 9);
        assertEquals(9, patched.getFieldValues().get("items.0.receivedQuantity"));

        userContext.set(outsider);
        assertThrows(OrderReviewLockedException.class,
                () -> service.patchField(orderId, "items.0.receivedQuantity", 10));
    }

    @Test
    void collaborationErrors_useLocalizedKeys() {
        Integer orderId = 606;
        User owner = buildUser(60, "chef.owner", Role.CHEF);
        User outsider = buildUser(61, "chef.out", Role.CHEF);

        userContext.set(owner);
        service.acquireLock(orderId);

        userContext.set(outsider);
        InvalidOperationException notPending = assertThrows(InvalidOperationException.class,
            () -> service.admitSharedReview(orderId, 999));
        assertEquals(MessageKey.ERROR_ORDER_COLLAB_NO_PERMISSION_ADMIT.getKey(), notPending.getMessage());

        userContext.set(owner);
        InvalidOperationException fieldPathRequired = assertThrows(InvalidOperationException.class,
                () -> service.lockField(orderId, " "));
        assertEquals(MessageKey.ERROR_ORDER_COLLAB_FIELD_PATH_REQUIRED.getKey(), fieldPathRequired.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupExpiredLocks_emitsFieldUnlockedEventForExpiredFieldLocks() throws Exception {
        Integer orderId = 707;
        User owner = buildUser(70, "chef.owner", Role.CHEF);

        userContext.set(owner);
        service.acquireLock(orderId);
        service.lockField(orderId, "detail:1:lot:0:quantity");

        Field collabMapField = OrderReviewLockService.class.getDeclaredField("collaborationByOrderId");
        collabMapField.setAccessible(true);
        Map<Integer, Object> collaborationByOrderId = (Map<Integer, Object>) collabMapField.get(service);
        Object collaborationEntry = collaborationByOrderId.get(orderId);
        assertNotNull(collaborationEntry);

        Field fieldLocksField = collaborationEntry.getClass().getDeclaredField("fieldLocksByPath");
        fieldLocksField.setAccessible(true);
        Map<String, Object> fieldLocks = (Map<String, Object>) fieldLocksField.get(collaborationEntry);
        Object fieldLockEntry = fieldLocks.get("detail:1:lot:0:quantity");
        assertNotNull(fieldLockEntry);

        Field expiresAtField = fieldLockEntry.getClass().getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.set(fieldLockEntry, LocalDateTime.now().minusSeconds(1));

        service.cleanupExpiredLocks();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/sync"), eventCaptor.capture());

        boolean unlockedByExpiryEmitted = eventCaptor.getAllValues().stream()
                .filter(RealtimeSyncEvent.class::isInstance)
                .map(RealtimeSyncEvent.class::cast)
                .anyMatch(event -> "order_collab".equals(event.getEntityType())
                        && "COLLAB_FIELD_UNLOCKED".equals(event.getAction())
                        && event.getMetadata() != null
                        && "detail:1:lot:0:quantity".equals(event.getMetadata().get("fieldPath"))
                        && "EXPIRED".equals(event.getMetadata().get("reason")));

        assertTrue(unlockedByExpiryEmitted, "Debe emitirse COLLAB_FIELD_UNLOCKED con reason=EXPIRED al limpiar locks vencidos");
    }

    private boolean tryAcquireWithBarrier(Integer orderId, User user, CountDownLatch start) throws Exception {
        userContext.set(user);
        try {
            start.await(3, TimeUnit.SECONDS);
            service.acquireLock(orderId);
            return true;
        } finally {
            userContext.remove();
        }
    }

    private User buildUser(Integer id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUser(username);
        user.setName(username);
        user.setRole(role);
        return user;
    }
}
