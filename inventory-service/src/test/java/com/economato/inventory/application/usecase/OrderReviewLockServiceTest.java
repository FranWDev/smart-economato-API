package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.economato.inventory.application.dto.response.OrderReviewLockResponseDTO;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.OrderReviewLockedException;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

@ExtendWith(MockitoExtension.class)
class OrderReviewLockServiceTest {

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderReviewLockService service;

    private final ThreadLocal<User> userContext = new ThreadLocal<>();

    @BeforeEach
    void setUp() {
        when(securityContextHelper.getCurrentUser()).thenAnswer(invocation -> userContext.get());
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
