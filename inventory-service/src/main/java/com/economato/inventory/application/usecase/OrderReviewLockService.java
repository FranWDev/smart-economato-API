package com.economato.inventory.application.usecase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.event.RealtimeSyncEvent;
import com.economato.inventory.application.dto.response.OrderReviewLockResponseDTO;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.OrderReviewLockedException;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final SecurityContextHelper securityContextHelper;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<Integer, OrderLockEntry> locksByOrderId = new ConcurrentHashMap<>();

    public OrderReviewLockResponseDTO getLockStatus(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);
        OrderLockEntry entry = locksByOrderId.get(orderId);
        return toResponse(orderId, entry, currentUser);
    }

    public OrderReviewLockResponseDTO heartbeatLock(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry entry = locksByOrderId.get(orderId);
        if (entry == null) {
            return toResponse(orderId, null, currentUser);
        }

        if (!isOwner(entry, currentUser)) {
            throw new OrderReviewLockedException(orderId, entry.lockedByDisplayName);
        }

        LocalDateTime now = LocalDateTime.now();
        entry.lastSeenAt = now;
        entry.expiresAt = now.plus(LOCK_TTL);
        return toResponse(orderId, entry, currentUser);
    }

    public OrderReviewLockResponseDTO acquireLock(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        AtomicBoolean createdNow = new AtomicBoolean(false);
        OrderLockEntry resolved = locksByOrderId.compute(orderId, (id, existing) -> {
            if (existing == null) {
                createdNow.set(true);
                return createEntry(orderId, currentUser);
            }

            if (isOwner(existing, currentUser)) {
                LocalDateTime now = LocalDateTime.now();
                existing.lastSeenAt = now;
                existing.expiresAt = now.plus(LOCK_TTL);
                return existing;
            }

            throw new OrderReviewLockedException(orderId, existing.lockedByDisplayName);
        });

        if (createdNow.get()) {
            broadcastLockEvent(orderId, "LOCK_ACQUIRED", currentUser.getName());
        }

        return toResponse(orderId, resolved, currentUser);
    }

    public OrderReviewLockResponseDTO releaseLock(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry existing = locksByOrderId.get(orderId);
        if (existing == null) {
            return toResponse(orderId, null, currentUser);
        }

        if (isOwner(existing, currentUser) || isAdmin(currentUser)) {
            locksByOrderId.remove(orderId);
            broadcastLockEvent(orderId, "LOCK_RELEASED", currentUser.getName());
            return toResponse(orderId, null, currentUser);
        }

        return toResponse(orderId, existing, currentUser);
    }

    public void releaseLocksForUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String normalized = username.trim().toLowerCase();
        locksByOrderId.entrySet().removeIf(entry -> {
            boolean ownedByUser = entry.getValue().lockedByUsername.equalsIgnoreCase(normalized);
            if (ownedByUser) {
                broadcastLockEvent(entry.getKey(), "LOCK_RELEASED", entry.getValue().lockedByUsername);
            }
            return ownedByUser;
        });
    }

    public void assertCanTransitionOrder(Integer orderId, OrderStatus targetStatus) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry existing = locksByOrderId.get(orderId);
        if (existing == null || isOwner(existing, currentUser)) {
            return;
        }

        if (isAdmin(currentUser) && targetStatus == OrderStatus.CONFIRMED) {
            return;
        }

        throw new OrderReviewLockedException(orderId, existing.lockedByDisplayName);
    }

    public void assertCanProcessReception(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry existing = locksByOrderId.get(orderId);
        if (existing == null || isOwner(existing, currentUser)) {
            return;
        }

        if (isAdmin(currentUser)) {
            return;
        }

        throw new OrderReviewLockedException(orderId, existing.lockedByDisplayName);
    }

    public void releaseLockAfterReviewCompletion(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        if (locksByOrderId.remove(orderId) != null) {
            broadcastLockEvent(orderId, "LOCK_RELEASED", currentUser.getName());
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredLocks() {
        LocalDateTime now = LocalDateTime.now();
        locksByOrderId.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt.isBefore(now);
            if (expired) {
                broadcastLockEvent(entry.getKey(), "LOCK_EXPIRED", entry.getValue().lockedByUsername);
            }
            return expired;
        });
    }

    private void cleanupIfExpired(Integer orderId) {
        OrderLockEntry entry = locksByOrderId.get(orderId);
        if (entry == null) {
            return;
        }

        if (entry.expiresAt.isBefore(LocalDateTime.now())) {
            locksByOrderId.remove(orderId);
            broadcastLockEvent(orderId, "LOCK_EXPIRED", entry.lockedByUsername);
        }
    }

    private OrderLockEntry createEntry(Integer orderId, User user) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderLockEntry(
                orderId,
                user.getId(),
                user.getName(),
                user.getName(),
                now,
            now,
                now.plus(LOCK_TTL));
    }

    private boolean isOwner(OrderLockEntry entry, User currentUser) {
        return entry.lockedByUserId.equals(currentUser.getId());
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private User getCurrentUserOrThrow() {
        User user = securityContextHelper.getCurrentUser();
        if (user == null) {
            throw new InvalidOperationException("Usuario autenticado no encontrado");
        }
        return user;
    }

    private OrderReviewLockResponseDTO toResponse(Integer orderId, OrderLockEntry entry, User currentUser) {
        if (entry == null) {
            return OrderReviewLockResponseDTO.builder()
                    .orderId(orderId)
                    .locked(false)
                    .currentUserOwner(false)
                    .currentUserAdmin(isAdmin(currentUser))
                    .build();
        }

        return OrderReviewLockResponseDTO.builder()
                .orderId(orderId)
                .locked(true)
                .lockedByUserId(entry.lockedByUserId)
                .lockedByUsername(entry.lockedByUsername)
                .lockedByDisplayName(entry.lockedByDisplayName)
                .acquiredAt(entry.acquiredAt)
                .lastSeenAt(entry.lastSeenAt)
                .expiresAt(entry.expiresAt)
                .currentUserOwner(entry.lockedByUserId.equals(currentUser.getId()))
                .currentUserAdmin(isAdmin(currentUser))
                .build();
    }

    private void broadcastLockEvent(Integer orderId, String action, String changedBy) {
        RealtimeSyncEvent event = RealtimeSyncEvent.builder()
                .entityType("order")
                .entityId(orderId)
                .action(action)
                .entityIds(Collections.emptyList())
                .affectedDomains(Collections.emptyList())
                .changedBy(changedBy)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/sync", event);
        } catch (RuntimeException e) {
            log.error("Failed to broadcast order lock event for orderId={}: {}", orderId, e.getMessage(), e);
        }
    }

    private static class OrderLockEntry {
        private final Integer orderId;
        private final Integer lockedByUserId;
        private final String lockedByUsername;
        private final String lockedByDisplayName;
        private final LocalDateTime acquiredAt;
        private LocalDateTime lastSeenAt;
        private LocalDateTime expiresAt;

        private OrderLockEntry(
                Integer orderId,
                Integer lockedByUserId,
                String lockedByUsername,
                String lockedByDisplayName,
                LocalDateTime acquiredAt,
                LocalDateTime lastSeenAt,
                LocalDateTime expiresAt) {
            this.orderId = orderId;
            this.lockedByUserId = lockedByUserId;
            this.lockedByUsername = lockedByUsername;
            this.lockedByDisplayName = lockedByDisplayName;
            this.acquiredAt = acquiredAt;
            this.lastSeenAt = lastSeenAt;
            this.expiresAt = expiresAt;
        }
    }
}
