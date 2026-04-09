package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.request.SendNotificationRequestDTO;
import com.economato.inventory.application.dto.response.NotificationResponseDTO;
import com.economato.inventory.application.dto.response.NotificationUnreadCountDTO;
import com.economato.inventory.application.mapper.NotificationMapper;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.NotificationSpecifications;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class PersistentNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;
    private final RoleNotificationService roleNotificationService;
    private final I18nService i18nService;

    public PersistentNotificationService(NotificationRepository notificationRepository,
                                        NotificationMapper notificationMapper,
                                        UserRepository userRepository,
                                        SecurityContextHelper securityContextHelper,
                                        RoleNotificationService roleNotificationService,
                                        I18nService i18nService) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.userRepository = userRepository;
        this.securityContextHelper = securityContextHelper;
        this.roleNotificationService = roleNotificationService;
        this.i18nService = i18nService;
    }

    public Notification createNotification(User recipient,
                                           User sender,
                                           NotificationType type,
                                           String title,
                                           String message,
                                           Long referenceId,
                                           String groupId) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .sender(sender)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .groupId(groupId)
                .isRead(false)
                .isDeletedByRecipient(false)
                .isDeletedBySender(false)
                .build();
        return notificationRepository.save(notification);
    }

    public void notifyUsersOfType(NotificationType type,
                                  String title,
                                  String message,
                                  Long referenceId,
                                  List<User> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        String groupId = recipients.size() > 1 ? UUID.randomUUID().toString() : null;
        List<Notification> notifications = recipients.stream()
                .filter(Objects::nonNull)
                .map(recipient -> Notification.builder()
                        .recipient(recipient)
                        .sender(null)
                        .type(type)
                        .title(title)
                        .message(message)
                        .referenceId(referenceId)
                        .groupId(groupId)
                        .isRead(false)
                        .isDeletedByRecipient(false)
                        .isDeletedBySender(false)
                        .build())
                .toList();

        if (notifications.isEmpty()) {
            return;
        }

        notificationRepository.saveAll(notifications);
        sendWebSocketNotifications(notifications);
    }

    public void sendManualNotification(SendNotificationRequestDTO request) {
        User sender = getCurrentUserOrThrow();

        if (request.getRecipientIds() != null && request.getRecipientIds().isEmpty() && request.getTargetRole() == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_INVALID_TARGET));
        }

        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();

        if (request.getRecipientIds() != null && !request.getRecipientIds().isEmpty()) {
            userRepository.findAllById(request.getRecipientIds()).stream()
                    .filter(user -> !user.isHidden())
                    .forEach(user -> recipientsById.put(user.getId(), user));
        }

        if (request.getTargetRole() != null) {
            userRepository.findByRoleAndIsHiddenFalse(request.getTargetRole())
                    .forEach(user -> recipientsById.put(user.getId(), user));
        }

        if (request.getRecipientIds() == null && request.getTargetRole() == null) {
            userRepository.findByIsHiddenFalse()
                    .forEach(user -> recipientsById.put(user.getId(), user));
        }

        List<User> recipients = new ArrayList<>(recipientsById.values());
        if (recipients.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_INVALID_TARGET));
        }

        String groupId = UUID.randomUUID().toString();
        List<Notification> notifications = recipients.stream()
                .map(recipient -> Notification.builder()
                        .recipient(recipient)
                        .sender(sender)
                        .type(NotificationType.MANUAL)
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .groupId(groupId)
                        .isRead(false)
                        .isDeletedByRecipient(false)
                        .isDeletedBySender(false)
                        .build())
                .toList();

        notificationRepository.saveAll(notifications);
        sendWebSocketNotifications(notifications);
    }

    public void markAsRead(Long notificationId) {
        User currentUser = getCurrentUserOrThrow();
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> buildOwnershipOrNotFound(notificationId));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead() {
        User currentUser = getCurrentUserOrThrow();
        notificationRepository.markAllAsReadByRecipientId(currentUser.getId());
    }

    public void deleteNotification(Long notificationId) {
        User currentUser = getCurrentUserOrThrow();
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> buildOwnershipOrNotFound(notificationId));

        notification.setDeletedByRecipient(true);
        notificationRepository.save(notification);
    }

    public void deleteManualNotificationGroup(String groupId) {
        User currentUser = getCurrentUserOrThrow();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_NOT_OWNER));
        }
        if (groupId == null || groupId.isBlank()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_INVALID_TARGET));
        }

        notificationRepository.softDeleteManualGroupByGroupIdAndSenderId(groupId, currentUser.getId());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponseDTO> getMyNotifications(NotificationType type,
                                                            Boolean isRead,
                                                            LocalDateTime from,
                                                            LocalDateTime to,
                                                            Pageable pageable) {
        User currentUser = getCurrentUserOrThrow();

        Specification<Notification> specification = Specification
                .where(NotificationSpecifications.hasRecipient(currentUser.getId()))
                .and(NotificationSpecifications.isNotDeletedByRecipient())
                .and(NotificationSpecifications.hasType(type))
                .and(NotificationSpecifications.isRead(isRead))
                .and(NotificationSpecifications.createdAfter(from))
                .and(NotificationSpecifications.createdBefore(to));

        Pageable normalized = pageable;
        if (normalized == null) {
            normalized = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        } else if (normalized.getSort().isUnsorted()) {
            normalized = PageRequest.of(normalized.getPageNumber(), normalized.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Page<NotificationResponseDTO> page = notificationRepository.findAll(specification, normalized)
                .map(notificationMapper::toResponseDTO);

        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountDTO getUnreadCount() {
        User currentUser = getCurrentUserOrThrow();
        long count = notificationRepository
                .countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(currentUser.getId());
        return NotificationUnreadCountDTO.builder()
                .count(count)
                .build();
    }

    public void notifyPlanCreated(WeeklyPlan plan) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_CREATED,
                new Object[]{plan.getChef().getName(), plan.getWeekStartDate()});
        List<User> recipients = userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN);
        notifyUsersOfType(NotificationType.WEEKLY_PLAN_CREATED, title, title, plan.getId(), recipients);
    }

    public void notifyPlanActivated(WeeklyPlan plan) {
        User actor = getCurrentUserOrThrow();
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_ACTIVATED,
                new Object[]{plan.getChef().getName(), plan.getWeekStartDate()});

        List<User> recipients;
        if (actor.getRole() == Role.ADMIN) {
            recipients = List.of(plan.getChef());
        } else {
            recipients = userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN);
        }

        recipients = recipients.stream()
                .filter(user -> !user.getId().equals(actor.getId()))
                .collect(Collectors.toList());

        notifyUsersOfType(NotificationType.WEEKLY_PLAN_ACTIVATED, title, title, plan.getId(), recipients);
    }

    public void notifySlotConfirmed(WeeklyPlan plan, WeeklyPlanSlot slot) {
        User actor = getCurrentUserOrThrow();
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_SLOT_CONFIRMED,
                new Object[]{plan.getChef().getName(), plan.getId(), slot.getDayOfWeek()});

        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)
                .forEach(user -> recipientsById.put(user.getId(), user));
        recipientsById.put(plan.getChef().getId(), plan.getChef());
        recipientsById.remove(actor.getId());

        notifyUsersOfType(NotificationType.WEEKLY_PLAN_SLOT_CONFIRMED, title, title, plan.getId(),
                new ArrayList<>(recipientsById.values()));
    }

    public void notifyDayConfirmed(WeeklyPlan plan, Integer dayOfWeek) {
        User actor = getCurrentUserOrThrow();
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_DAY_CONFIRMED,
                new Object[]{plan.getChef().getName(), dayOfWeek, plan.getId()});

        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)
                .forEach(user -> recipientsById.put(user.getId(), user));
        recipientsById.put(plan.getChef().getId(), plan.getChef());
        recipientsById.remove(actor.getId());

        notifyUsersOfType(NotificationType.WEEKLY_PLAN_DAY_CONFIRMED, title, title, plan.getId(),
                new ArrayList<>(recipientsById.values()));
    }

    public void notifyPlanCompleted(WeeklyPlan plan) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_COMPLETED,
                new Object[]{plan.getChef().getName(), plan.getId()});

        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)
                .forEach(user -> recipientsById.put(user.getId(), user));
        recipientsById.put(plan.getChef().getId(), plan.getChef());

        notifyUsersOfType(NotificationType.WEEKLY_PLAN_COMPLETED, title, title, plan.getId(),
                new ArrayList<>(recipientsById.values()));
    }

    public void notifyCrisis(String title, String message, AlertCode code, Long crisisId) {
        NotificationType type = code == AlertCode.FOOD_CRISIS_LIFTED
                ? NotificationType.FOOD_CRISIS_LIFTED
                : NotificationType.FOOD_CRISIS_ACTIVATED;

        List<User> recipients = userRepository.findByIsHiddenFalse();
        notifyUsersOfType(type, title, message, crisisId, recipients);
    }

    public void notifyStockPrediction(int productCount) {
        String message = i18nService.getMessage(MessageKey.NOTIFICATION_PREDICTION_TRIGGERED,
                new Object[]{productCount});
        List<User> recipients = userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN);
        notifyUsersOfType(NotificationType.STOCK_PREDICTION_TRIGGERED, message, message, null, recipients);
    }

    public void notifyIncidentCreated(Incident incident) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_INCIDENT_CREATED,
                new Object[]{incident.getTitle(), incident.getId()});
        List<User> recipients = userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN).stream()
                .filter(user -> incident.getCreatedBy() == null || !user.getId().equals(incident.getCreatedBy().getId()))
                .toList();
        notifyUsersOfType(NotificationType.INCIDENT_CREATED, title, title, incident.getId(), recipients);
        roleNotificationService.sendNotificationToRole(Role.ADMIN, title, title);
    }

    public void notifyIncidentOpened(Incident incident) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_INCIDENT_OPENED,
                new Object[]{incident.getTitle(), incident.getId()});
        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        if (incident.getCreatedBy() != null) {
            recipientsById.put(incident.getCreatedBy().getId(), incident.getCreatedBy());
        }
        if (incident.getRelatedTeacher() != null) {
            recipientsById.put(incident.getRelatedTeacher().getId(), incident.getRelatedTeacher());
        }
        if (incident.getOpenedBy() != null) {
            recipientsById.remove(incident.getOpenedBy().getId());
        }
        notifyUsersOfType(NotificationType.INCIDENT_OPENED, title, title, incident.getId(), new ArrayList<>(recipientsById.values()));
    }

    public void notifyIncidentClosed(Incident incident) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_INCIDENT_CLOSED,
                new Object[]{incident.getTitle(), incident.getId()});
        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        if (incident.getCreatedBy() != null) {
            recipientsById.put(incident.getCreatedBy().getId(), incident.getCreatedBy());
        }
        if (incident.getRelatedTeacher() != null) {
            recipientsById.put(incident.getRelatedTeacher().getId(), incident.getRelatedTeacher());
        }
        if (incident.getClosedBy() != null) {
            recipientsById.remove(incident.getClosedBy().getId());
        }
        notifyUsersOfType(NotificationType.INCIDENT_CLOSED, title, title, incident.getId(), new ArrayList<>(recipientsById.values()));
    }

    public void notifyIncidentChatMessage(Incident incident, User author) {
        String title = i18nService.getMessage(MessageKey.NOTIFICATION_INCIDENT_CHAT_MESSAGE,
                new Object[]{incident.getTitle(), incident.getId()});

        LinkedHashMap<Integer, User> recipientsById = new LinkedHashMap<>();
        userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)
                .forEach(user -> recipientsById.put(user.getId(), user));
        if (incident.getCreatedBy() != null) {
            recipientsById.put(incident.getCreatedBy().getId(), incident.getCreatedBy());
        }
        if (incident.getRelatedTeacher() != null) {
            recipientsById.put(incident.getRelatedTeacher().getId(), incident.getRelatedTeacher());
        }
        if (author != null) {
            recipientsById.remove(author.getId());
        }

        notifyUsersOfType(NotificationType.INCIDENT_CHAT_MESSAGE, title, title, incident.getId(),
                new ArrayList<>(recipientsById.values()));
    }

    private RuntimeException buildOwnershipOrNotFound(Long notificationId) {
        if (notificationRepository.existsById(notificationId)) {
            return new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_NOT_OWNER));
        }
        return new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_NOTIFICATION_NOT_FOUND));
    }

    private User getCurrentUserOrThrow() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
        }
        return currentUser;
    }

    private void sendWebSocketNotifications(List<Notification> notifications) {
        for (Notification notification : notifications) {
            try {
                roleNotificationService.sendNotificationToUser(
                        notification.getRecipient().getName(),
                        notification.getTitle(),
                        notification.getMessage());
            } catch (Exception ex) {
                log.error("Failed to send WebSocket notification to user id={}",
                        notification.getRecipient().getId(), ex);
            }
        }
    }
}
