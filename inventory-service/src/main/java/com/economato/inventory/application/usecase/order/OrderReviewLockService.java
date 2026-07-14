package com.economato.inventory.application.usecase.order;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.shared.event.RealtimeSyncEvent;
import com.economato.inventory.application.dto.order.response.OrderCollaborationFieldLockResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderCollaborationUserResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderReviewLockResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderReviewCollaborationStateResponseDTO;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.order.exception.OrderCollaborationFieldLockedException;
import com.economato.inventory.infrastructure.adapter.in.web.order.exception.OrderReviewLockedException;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration FIELD_LOCK_TTL = Duration.ofSeconds(45);

    private final SecurityContextHelper securityContextHelper;
    private final SimpMessagingTemplate messagingTemplate;
    private final I18nService i18nService;

    private final Map<Integer, OrderLockEntry> locksByOrderId = new ConcurrentHashMap<>();
    private final Map<Integer, OrderCollaborationEntry> collaborationByOrderId = new ConcurrentHashMap<>();

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
            ensureCollaborationEntry(orderId, resolved).participantsByUserId.put(
                    currentUser.getId(),
                    createParticipant(currentUser));
            broadcastLockEvent(orderId, "LOCK_ACQUIRED", currentUser.getName());
        } else {
            ensureCollaborationEntry(orderId, resolved).participantsByUserId.putIfAbsent(
                    currentUser.getId(),
                    createParticipant(currentUser));
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
            collaborationByOrderId.remove(orderId);
            broadcastLockEvent(orderId, "LOCK_RELEASED", currentUser.getName());
            broadcastCollaborationEvent(orderId, "COLLAB_STATE_CLEARED", currentUser.getName(),
                    Map.of("reason", "LOCK_RELEASED"));
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
                collaborationByOrderId.remove(entry.getKey());
                broadcastLockEvent(entry.getKey(), "LOCK_RELEASED", entry.getValue().lockedByUsername);
                broadcastCollaborationEvent(entry.getKey(), "COLLAB_STATE_CLEARED", entry.getValue().lockedByUsername,
                        Map.of("reason", "OWNER_DISCONNECTED"));
            }
            return ownedByUser;
        });

        // If user was only collaborator (not lock owner), remove collaborative presence.
        collaborationByOrderId.forEach((orderId, collaborationEntry) -> {
            CollabParticipant removed = collaborationEntry.participantsByUserId.values().stream()
                    .filter(participant -> participant.username.equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(null);

            if (removed == null) {
                return;
            }

            collaborationEntry.participantsByUserId.remove(removed.userId);
            collaborationEntry.pendingRequestsByUserId.remove(removed.userId);
            collaborationEntry.fieldLocksByPath.entrySet().removeIf(fieldEntry ->
                    fieldEntry.getValue().lockedByUserId.equals(removed.userId));

            broadcastCollaborationEvent(orderId, "COLLAB_PARTICIPANT_LEFT", removed.username,
                    Map.of("userId", removed.userId, "username", removed.username));
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
            collaborationByOrderId.remove(orderId);
            broadcastLockEvent(orderId, "LOCK_RELEASED", currentUser.getName());
            broadcastCollaborationEvent(orderId, "COLLAB_STATE_CLEARED", currentUser.getName(),
                    Map.of("reason", "REVIEW_COMPLETED"));
        }
    }

    public OrderReviewCollaborationStateResponseDTO getCollaborationState(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = locksByOrderId.get(orderId);
        if (lockEntry == null) {
            return OrderReviewCollaborationStateResponseDTO.builder()
                    .orderId(orderId)
                    .locked(false)
                    .currentUserOwner(false)
                    .currentUserAdmin(isAdmin(currentUser))
                    .currentUserCollaborator(false)
                    .currentUserCanAdmit(false)
                    .collaborators(Collections.emptyList())
                    .pendingRequests(Collections.emptyList())
                    .fieldLocks(Collections.emptyList())
                    .fieldValues(Collections.emptyMap())
                    .build();
        }

        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);
        cleanupExpiredFieldLocks(orderId, collaborationEntry);
        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    public OrderReviewCollaborationStateResponseDTO requestSharedReview(Integer orderId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = getRequiredLock(orderId);
        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);

        if (isOwner(lockEntry, currentUser) || isAdmin(currentUser)
                || collaborationEntry.participantsByUserId.containsKey(currentUser.getId())) {
            return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
        }

        collaborationEntry.pendingRequestsByUserId.putIfAbsent(currentUser.getId(), createParticipant(currentUser));
        broadcastCollaborationEvent(orderId, "COLLAB_REQUESTED", currentUser.getName(),
                Map.of("userId", currentUser.getId(), "username", currentUser.getName()));
        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    public OrderReviewCollaborationStateResponseDTO admitSharedReview(Integer orderId, Integer userId) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = getRequiredLock(orderId);
        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);

        if (!canAdmit(lockEntry, collaborationEntry, currentUser)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_NO_PERMISSION_ADMIT));
        }

        CollabParticipant requested = collaborationEntry.pendingRequestsByUserId.remove(userId);
        if (requested == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_REQUEST_NOT_FOUND));
        }

        collaborationEntry.participantsByUserId.put(requested.userId, requested);
        broadcastCollaborationEvent(orderId, "COLLAB_ADMITTED", currentUser.getName(),
                Map.of("userId", requested.userId, "username", requested.username));
        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    public OrderReviewCollaborationStateResponseDTO lockField(Integer orderId, String fieldPath) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = getRequiredLock(orderId);
        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);
        ensureCanCollaborate(orderId, lockEntry, collaborationEntry, currentUser);

        String normalizedFieldPath = normalizeFieldPath(fieldPath);
        cleanupExpiredFieldLocks(orderId, collaborationEntry);

        LocalDateTime now = LocalDateTime.now();
        FieldLockEntry existing = collaborationEntry.fieldLocksByPath.get(normalizedFieldPath);
        if (existing != null && !existing.lockedByUserId.equals(currentUser.getId()) && !isAdmin(currentUser)) {
            throw new OrderCollaborationFieldLockedException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED, existing.lockedByDisplayName),
                    orderId,
                    normalizedFieldPath,
                    existing.lockedByDisplayName);
        }

        FieldLockEntry lock = new FieldLockEntry(
                normalizedFieldPath,
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getName(),
                now,
                now.plus(FIELD_LOCK_TTL));
        collaborationEntry.fieldLocksByPath.put(normalizedFieldPath, lock);

        broadcastCollaborationEvent(orderId, "COLLAB_FIELD_LOCKED", currentUser.getName(),
                Map.of("fieldPath", normalizedFieldPath,
                        "lockedByUserId", currentUser.getId(),
                        "lockedByUsername", currentUser.getName()));

        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    public OrderReviewCollaborationStateResponseDTO unlockField(Integer orderId, String fieldPath) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = getRequiredLock(orderId);
        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);
        ensureCanCollaborate(orderId, lockEntry, collaborationEntry, currentUser);

        String normalizedFieldPath = normalizeFieldPath(fieldPath);
        FieldLockEntry existing = collaborationEntry.fieldLocksByPath.get(normalizedFieldPath);
        if (existing == null) {
            return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
        }

        if (!existing.lockedByUserId.equals(currentUser.getId()) && !isAdmin(currentUser)) {
            throw new OrderCollaborationFieldLockedException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED, existing.lockedByDisplayName),
                    orderId,
                    normalizedFieldPath,
                    existing.lockedByDisplayName);
        }

        collaborationEntry.fieldLocksByPath.remove(normalizedFieldPath);
        broadcastCollaborationEvent(orderId, "COLLAB_FIELD_UNLOCKED", currentUser.getName(),
                Map.of("fieldPath", normalizedFieldPath));

        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    public OrderReviewCollaborationStateResponseDTO patchField(Integer orderId, String fieldPath, Object value) {
        User currentUser = getCurrentUserOrThrow();
        cleanupIfExpired(orderId);

        OrderLockEntry lockEntry = getRequiredLock(orderId);
        OrderCollaborationEntry collaborationEntry = ensureCollaborationEntry(orderId, lockEntry);
        ensureCanCollaborate(orderId, lockEntry, collaborationEntry, currentUser);

        String normalizedFieldPath = normalizeFieldPath(fieldPath);
        cleanupExpiredFieldLocks(orderId, collaborationEntry);

        FieldLockEntry existing = collaborationEntry.fieldLocksByPath.get(normalizedFieldPath);
        if (existing != null && !existing.lockedByUserId.equals(currentUser.getId()) && !isAdmin(currentUser)) {
            throw new OrderCollaborationFieldLockedException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED, existing.lockedByDisplayName),
                    orderId,
                    normalizedFieldPath,
                    existing.lockedByDisplayName);
        }

        collaborationEntry.fieldValuesByPath.put(normalizedFieldPath, value);
        broadcastCollaborationEvent(orderId, "COLLAB_FIELD_PATCHED", currentUser.getName(),
                Map.of("fieldPath", normalizedFieldPath, "value", value));

        return toCollaborationResponse(orderId, lockEntry, collaborationEntry, currentUser);
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredLocks() {
        LocalDateTime now = LocalDateTime.now();
        locksByOrderId.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt.isBefore(now);
            if (expired) {
                collaborationByOrderId.remove(entry.getKey());
                broadcastLockEvent(entry.getKey(), "LOCK_EXPIRED", entry.getValue().lockedByUsername);
                broadcastCollaborationEvent(entry.getKey(), "COLLAB_STATE_CLEARED", entry.getValue().lockedByUsername,
                        Map.of("reason", "LOCK_EXPIRED"));
            }
            return expired;
        });

        collaborationByOrderId.forEach((orderId, collaborationEntry) -> cleanupExpiredFieldLocks(orderId, collaborationEntry));
    }

    private void cleanupIfExpired(Integer orderId) {
        OrderLockEntry entry = locksByOrderId.get(orderId);
        if (entry == null) {
            return;
        }

        if (entry.expiresAt.isBefore(LocalDateTime.now())) {
            locksByOrderId.remove(orderId);
            collaborationByOrderId.remove(orderId);
            broadcastLockEvent(orderId, "LOCK_EXPIRED", entry.lockedByUsername);
            broadcastCollaborationEvent(orderId, "COLLAB_STATE_CLEARED", entry.lockedByUsername,
                    Map.of("reason", "LOCK_EXPIRED"));
        }
    }

    private OrderLockEntry getRequiredLock(Integer orderId) {
        OrderLockEntry lockEntry = locksByOrderId.get(orderId);
        if (lockEntry == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_NOT_ACTIVE));
        }
        return lockEntry;
    }

    private OrderCollaborationEntry ensureCollaborationEntry(Integer orderId, OrderLockEntry lockEntry) {
        return collaborationByOrderId.computeIfAbsent(orderId, ignored -> {
            OrderCollaborationEntry entry = new OrderCollaborationEntry();
            entry.participantsByUserId.put(lockEntry.lockedByUserId,
                    new CollabParticipant(
                            lockEntry.lockedByUserId,
                            lockEntry.lockedByUsername,
                            lockEntry.lockedByDisplayName,
                            lockEntry.acquiredAt));
            return entry;
        });
    }

    private void ensureCanCollaborate(Integer orderId, OrderLockEntry lockEntry, OrderCollaborationEntry collaborationEntry, User user) {
        if (isOwner(lockEntry, user) || isAdmin(user)
                || collaborationEntry.participantsByUserId.containsKey(user.getId())) {
            return;
        }

        throw new OrderReviewLockedException(orderId, lockEntry.lockedByDisplayName);
    }

    private boolean canAdmit(OrderLockEntry lockEntry, OrderCollaborationEntry collaborationEntry, User user) {
        return isOwner(lockEntry, user)
                || isAdmin(user)
                || collaborationEntry.participantsByUserId.containsKey(user.getId());
    }

    private CollabParticipant createParticipant(User user) {
        return new CollabParticipant(user.getId(), user.getName(), user.getName(), LocalDateTime.now());
    }

    private String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_COLLAB_FIELD_PATH_REQUIRED));
        }
        return fieldPath.trim();
    }

    private void cleanupExpiredFieldLocks(Integer orderId, OrderCollaborationEntry collaborationEntry) {
        LocalDateTime now = LocalDateTime.now();
        collaborationEntry.fieldLocksByPath.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt.isBefore(now);
            if (expired) {
                broadcastCollaborationEvent(orderId, "COLLAB_FIELD_UNLOCKED", entry.getValue().lockedByUsername,
                        Map.of("fieldPath", entry.getValue().fieldPath, "reason", "EXPIRED"));
            }
            return expired;
        });
    }

    private OrderReviewCollaborationStateResponseDTO toCollaborationResponse(
            Integer orderId,
            OrderLockEntry lockEntry,
            OrderCollaborationEntry collaborationEntry,
            User currentUser) {
        List<OrderCollaborationUserResponseDTO> collaborators = new ArrayList<>(
                collaborationEntry.participantsByUserId.values().stream()
                        .map(this::toCollaboratorResponse)
                        .toList());
        collaborators.sort(Comparator.comparing(OrderCollaborationUserResponseDTO::getJoinedAt));

        List<OrderCollaborationUserResponseDTO> pendingRequests = new ArrayList<>(
                collaborationEntry.pendingRequestsByUserId.values().stream()
                        .map(this::toCollaboratorResponse)
                        .toList());
        pendingRequests.sort(Comparator.comparing(OrderCollaborationUserResponseDTO::getJoinedAt));

        List<OrderCollaborationFieldLockResponseDTO> fieldLocks = new ArrayList<>(
                collaborationEntry.fieldLocksByPath.values().stream()
                        .map(this::toFieldLockResponse)
                        .toList());
        fieldLocks.sort(Comparator.comparing(OrderCollaborationFieldLockResponseDTO::getFieldPath));

        boolean currentUserCollaborator = collaborationEntry.participantsByUserId.containsKey(currentUser.getId());

        return OrderReviewCollaborationStateResponseDTO.builder()
                .orderId(orderId)
                .locked(true)
                .lockedByUserId(lockEntry.lockedByUserId)
                .lockedByUsername(lockEntry.lockedByUsername)
                .lockedByDisplayName(lockEntry.lockedByDisplayName)
                .currentUserOwner(isOwner(lockEntry, currentUser))
                .currentUserAdmin(isAdmin(currentUser))
                .currentUserCollaborator(currentUserCollaborator)
                .currentUserCanAdmit(canAdmit(lockEntry, collaborationEntry, currentUser))
                .collaborators(collaborators)
                .pendingRequests(pendingRequests)
                .fieldLocks(fieldLocks)
                .fieldValues(Collections.unmodifiableMap(new ConcurrentHashMap<>(collaborationEntry.fieldValuesByPath)))
                .build();
    }

    private OrderCollaborationUserResponseDTO toCollaboratorResponse(CollabParticipant participant) {
        return OrderCollaborationUserResponseDTO.builder()
                .userId(participant.userId)
                .username(participant.username)
                .displayName(participant.displayName)
                .joinedAt(participant.joinedAt)
                .build();
    }

    private OrderCollaborationFieldLockResponseDTO toFieldLockResponse(FieldLockEntry entry) {
        return OrderCollaborationFieldLockResponseDTO.builder()
                .fieldPath(entry.fieldPath)
                .lockedByUserId(entry.lockedByUserId)
                .lockedByUsername(entry.lockedByUsername)
                .lockedByDisplayName(entry.lockedByDisplayName)
                .lockedAt(entry.lockedAt)
                .expiresAt(entry.expiresAt)
                .build();
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
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_NOT_AUTHENTICATED));
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

    private void broadcastCollaborationEvent(Integer orderId, String action, String changedBy, Map<String, Object> metadata) {
        RealtimeSyncEvent event = RealtimeSyncEvent.builder()
                .entityType("order_collab")
                .entityId(orderId)
                .action(action)
                .entityIds(Collections.emptyList())
                .affectedDomains(Collections.emptyList())
                .changedBy(changedBy)
                .timestamp(LocalDateTime.now())
                .metadata(metadata == null ? Collections.emptyMap() : metadata)
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/sync", event);
        } catch (RuntimeException e) {
            log.error("Failed to broadcast order collaboration event for orderId={}: {}", orderId, e.getMessage(), e);
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

    private static class OrderCollaborationEntry {
        private final Map<Integer, CollabParticipant> participantsByUserId = new ConcurrentHashMap<>();
        private final Map<Integer, CollabParticipant> pendingRequestsByUserId = new ConcurrentHashMap<>();
        private final Map<String, FieldLockEntry> fieldLocksByPath = new ConcurrentHashMap<>();
        private final Map<String, Object> fieldValuesByPath = new ConcurrentHashMap<>();
    }

    private static class CollabParticipant {
        private final Integer userId;
        private final String username;
        private final String displayName;
        private final LocalDateTime joinedAt;

        private CollabParticipant(Integer userId, String username, String displayName, LocalDateTime joinedAt) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.joinedAt = joinedAt;
        }
    }

    private static class FieldLockEntry {
        private final String fieldPath;
        private final Integer lockedByUserId;
        private final String lockedByUsername;
        private final String lockedByDisplayName;
        private final LocalDateTime lockedAt;
        private final LocalDateTime expiresAt;

        private FieldLockEntry(
                String fieldPath,
                Integer lockedByUserId,
                String lockedByUsername,
                String lockedByDisplayName,
                LocalDateTime lockedAt,
                LocalDateTime expiresAt) {
            this.fieldPath = fieldPath;
            this.lockedByUserId = lockedByUserId;
            this.lockedByUsername = lockedByUsername;
            this.lockedByDisplayName = lockedByDisplayName;
            this.lockedAt = lockedAt;
            this.expiresAt = expiresAt;
        }
    }
}
