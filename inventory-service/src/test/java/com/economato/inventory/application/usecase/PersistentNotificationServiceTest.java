package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.SendNotificationRequestDTO;
import com.economato.inventory.application.dto.response.NotificationResponseDTO;
import com.economato.inventory.application.dto.response.NotificationUnreadCountDTO;
import com.economato.inventory.application.mapper.NotificationMapper;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private RoleNotificationService roleNotificationService;
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private PersistentNotificationService service;

    private User currentUser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        currentUser = new User();
        currentUser.setId(1);
        currentUser.setName("current-user");
        currentUser.setRole(Role.ADMIN);

        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
    }

    @Test
    void getMyNotifications_ShouldReturnPageOfNotifications() {
        Notification notification = Notification.builder()
                .id(10L)
                .recipient(currentUser)
                .type(NotificationType.MANUAL)
                .title("title")
                .message("message")
                .build();
        NotificationResponseDTO dto = NotificationResponseDTO.builder().id(10L).build();

        when(notificationRepository.findAll(specAny(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));
        when(notificationMapper.toResponseDTO(notification)).thenReturn(dto);

        Page<NotificationResponseDTO> result = service.getMyNotifications(null, null, null, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(10L, result.getContent().get(0).getId());
        verify(notificationRepository).findAll(specAny(), any(Pageable.class));
        verify(notificationMapper).toResponseDTO(notification);
    }

    @Test
    void getUnreadCount_ShouldReturnCount() {
        when(notificationRepository.countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(1)).thenReturn(7L);

        NotificationUnreadCountDTO result = service.getUnreadCount();

        assertEquals(7L, result.getCount());
        verify(notificationRepository).countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(1);
    }

    @Test
    void markAsRead_ShouldUpdateNotification() {
        Notification notification = Notification.builder().id(12L).recipient(currentUser).isRead(false).build();
        when(notificationRepository.findByIdAndRecipientId(12L, 1)).thenReturn(Optional.of(notification));

        service.markAsRead(12L);

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_WhenNotOwner_ShouldThrow() {
        when(notificationRepository.findByIdAndRecipientId(12L, 1)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(12L)).thenReturn(true);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.markAsRead(12L));
        assertNotNull(ex);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllAsRead_ShouldCallBulkUpdate() {
        service.markAllAsRead();

        verify(notificationRepository).markAllAsReadByRecipientId(1);
    }

    @Test
    void deleteNotification_ShouldSoftDelete() {
        Notification notification = Notification.builder().id(9L).recipient(currentUser).isDeletedByRecipient(false).build();
        when(notificationRepository.findByIdAndRecipientId(9L, 1)).thenReturn(Optional.of(notification));

        service.deleteNotification(9L);

        assertTrue(notification.isDeletedByRecipient());
        verify(notificationRepository).save(notification);
    }

    @Test
    void sendManualNotification_ShouldPersistAndSendWS() {
        User recipientA = new User();
        recipientA.setId(2);
        recipientA.setName("recipient-a");
        recipientA.setRole(Role.ADMIN);

        User recipientB = new User();
        recipientB.setId(3);
        recipientB.setName("recipient-b");
        recipientB.setRole(Role.ADMIN);

        SendNotificationRequestDTO request = new SendNotificationRequestDTO("Title", "Body", null, Role.ADMIN);
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(recipientA, recipientB));

        service.sendManualNotification(request);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> saved = captor.getValue();

        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(n -> n.getType() == NotificationType.MANUAL));
        assertTrue(saved.stream().allMatch(n -> n.getGroupId() != null));
        assertTrue(saved.stream().allMatch(n -> n.getSender().getId().equals(1)));

        verify(roleNotificationService).sendNotificationToUser(eq("recipient-a"), eq("Title"), eq("Body"));
        verify(roleNotificationService).sendNotificationToUser(eq("recipient-b"), eq("Title"), eq("Body"));
    }

    @Test
    void notifyPlanCreated_ShouldCreateNotificationsForAdmins() {
        User adminA = new User();
        adminA.setId(2);
        adminA.setName("admin-a");
        adminA.setRole(Role.ADMIN);

        User adminB = new User();
        adminB.setId(3);
        adminB.setName("admin-b");
        adminB.setRole(Role.ADMIN);

        WeeklyPlan plan = WeeklyPlan.builder()
                .id(100L)
                .chef(currentUser)
                .weekStartDate(LocalDateTime.now().toLocalDate())
                .build();

        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(adminA, adminB));

        service.notifyPlanCreated(plan);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());

        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(n -> n.getType() == NotificationType.WEEKLY_PLAN_CREATED));
        assertTrue(captor.getValue().stream().allMatch(n -> n.getReferenceId().equals(100L)));
    }

    @Test
    void notifyCrisis_ShouldCreateNotificationsForAllUsers() {
        User u1 = new User();
        u1.setId(2);
        u1.setName("u1");
        User u2 = new User();
        u2.setId(3);
        u2.setName("u2");

        when(userRepository.findByIsHiddenFalse()).thenReturn(List.of(u1, u2));

        service.notifyCrisis("crisis-title", "crisis-body", AlertCode.FOOD_CRISIS_ACTIVATED, 50L);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());

        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(n -> n.getType() == NotificationType.FOOD_CRISIS_ACTIVATED));
        assertTrue(captor.getValue().stream().allMatch(n -> n.getReferenceId().equals(50L)));
    }

    @Test
    void getMyNotifications_WhenPageableUnsorted_ShouldApplyCreatedAtDesc() {
        when(notificationRepository.findAll(specAny(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        service.getMyNotifications(null, null, null, null, PageRequest.of(0, 5, Sort.unsorted()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(specAny(), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertTrue(used.getSort().isSorted());
        assertEquals(Sort.Direction.DESC, used.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void getMyNotifications_WhenPageableNull_ShouldUseDefaultPageAndSort() {
        when(notificationRepository.findAll(specAny(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getMyNotifications(null, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(specAny(), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(20, used.getPageSize());
        assertEquals(Sort.Direction.DESC, used.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void markAsRead_WhenNotFound_ShouldThrowResourceNotFound() {
        when(notificationRepository.findByIdAndRecipientId(99L, 1)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.markAsRead(99L));
        assertNotNull(ex);
    }

    @Test
    void deleteNotification_WhenNotFound_ShouldThrowResourceNotFound() {
        when(notificationRepository.findByIdAndRecipientId(111L, 1)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(111L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.deleteNotification(111L));
        assertNotNull(ex);
    }

    @Test
    void deleteManualNotificationGroup_WhenNonAdmin_ShouldThrowAccessDenied() {
        currentUser.setRole(Role.CHEF);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.deleteManualNotificationGroup("group-1"));
        assertNotNull(ex);
        verify(notificationRepository, never()).softDeleteManualGroupByGroupIdAndSenderId(anyString(), anyInt());
    }

    @Test
    void deleteManualNotificationGroup_WhenGroupIdBlank_ShouldThrowInvalidOperation() {
        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> service.deleteManualNotificationGroup("  "));
        assertNotNull(ex);
        verify(notificationRepository, never()).softDeleteManualGroupByGroupIdAndSenderId(anyString(), anyInt());
    }

    @Test
    void deleteManualNotificationGroup_ShouldUseBulkUpdate() {
        service.deleteManualNotificationGroup("group-x");

        verify(notificationRepository).softDeleteManualGroupByGroupIdAndSenderId("group-x", 1);
    }

    @Test
    void sendManualNotification_WhenRecipientIdsEmptyAndRoleNull_ShouldThrow() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO("t", "m", List.of(), null);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> service.sendManualNotification(request));
        assertNotNull(ex);
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void sendManualNotification_WhenNoTargetsResolved_ShouldThrow() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO("t", "m", null, Role.CHEF);
        when(userRepository.findByRoleAndIsHiddenFalse(Role.CHEF)).thenReturn(List.of());

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> service.sendManualNotification(request));
        assertNotNull(ex);
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void sendManualNotification_WhenCurrentUserNull_ShouldThrowAccessDenied() {
        when(securityContextHelper.getCurrentUser()).thenReturn(null);
        SendNotificationRequestDTO request = new SendNotificationRequestDTO("t", "m", null, Role.ADMIN);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.sendManualNotification(request));
        assertNotNull(ex);
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void sendManualNotification_WithRoleAndRecipientIds_ShouldDeduplicateRecipients() {
        User recipientA = new User();
        recipientA.setId(2);
        recipientA.setName("a");

        User recipientB = new User();
        recipientB.setId(3);
        recipientB.setName("b");

        when(userRepository.findAllById(List.of(2, 3))).thenReturn(List.of(recipientA, recipientB));
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(recipientA));

        service.sendManualNotification(new SendNotificationRequestDTO("title", "msg", List.of(2, 3), Role.ADMIN));

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        Set<Integer> recipientIds = captor.getValue().stream()
                .map(n -> n.getRecipient().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(2, 3), recipientIds);
    }

    @Test
    void sendManualNotification_WhenBroadcastAll_ShouldUseVisibleUsers() {
        User recipientA = new User();
        recipientA.setId(2);
        recipientA.setName("a");
        User recipientB = new User();
        recipientB.setId(3);
        recipientB.setName("b");

        when(userRepository.findByIsHiddenFalse()).thenReturn(List.of(recipientA, recipientB));

        service.sendManualNotification(new SendNotificationRequestDTO("title", "msg", null, null));

        verify(userRepository).findByIsHiddenFalse();
        verify(notificationRepository).saveAll(any());
    }

    @Test
    void notifyUsersOfType_WhenRecipientsNull_ShouldDoNothing() {
        service.notifyUsersOfType(NotificationType.MANUAL, "t", "m", null, null);

        verify(notificationRepository, never()).saveAll(any());
        verify(roleNotificationService, never()).sendNotificationToUser(anyString(), anyString(), anyString());
    }

    @Test
    void notifyUsersOfType_WhenSingleRecipient_ShouldNotAssignGroupId() {
        User recipient = new User();
        recipient.setId(5);
        recipient.setName("single");

        service.notifyUsersOfType(NotificationType.MANUAL, "t", "m", 7L, List.of(recipient));

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertNull(captor.getValue().get(0).getGroupId());
    }

    @Test
    void notifyUsersOfType_WhenMultipleRecipients_ShouldAssignSameGroupId() {
        User r1 = new User();
        r1.setId(10);
        r1.setName("r1");
        User r2 = new User();
        r2.setId(11);
        r2.setName("r2");

        service.notifyUsersOfType(NotificationType.MANUAL, "t", "m", 22L, List.of(r1, r2));

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getGroupId());
        assertEquals(saved.get(0).getGroupId(), saved.get(1).getGroupId());
    }

    @Test
    void notifyPlanActivated_WhenActorIsAdmin_ShouldNotifyChefOnly() {
        User chef = new User();
        chef.setId(20);
        chef.setName("chef");
        WeeklyPlan plan = WeeklyPlan.builder().id(2L).chef(chef).weekStartDate(LocalDate.now()).build();

        currentUser.setRole(Role.ADMIN);
        service.notifyPlanActivated(plan);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(20, captor.getValue().get(0).getRecipient().getId());
    }

    @Test
    void notifyPlanActivated_WhenActorIsChef_ShouldNotifyAdminsExcludingActor() {
        User adminA = new User();
        adminA.setId(2);
        adminA.setName("admin-a");
        adminA.setRole(Role.ADMIN);
        User adminB = new User();
        adminB.setId(3);
        adminB.setName("admin-b");
        adminB.setRole(Role.ADMIN);

        currentUser.setRole(Role.CHEF);

        WeeklyPlan plan = WeeklyPlan.builder().id(8L).chef(currentUser).weekStartDate(LocalDate.now()).build();
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(adminA, adminB, currentUser));

        service.notifyPlanActivated(plan);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        Set<Integer> ids = captor.getValue().stream().map(n -> n.getRecipient().getId()).collect(Collectors.toSet());
        assertEquals(Set.of(2, 3), ids);
    }

    @Test
    void notifyStockPrediction_ShouldNotifyAdmins() {
        User adminA = new User();
        adminA.setId(2);
        adminA.setName("admin-a");
        adminA.setRole(Role.ADMIN);

        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(adminA));

        service.notifyStockPrediction(15);

        verify(notificationRepository).saveAll(any());
        verify(roleNotificationService).sendNotificationToUser(eq("admin-a"), anyString(), anyString());
    }

    @Test
    void notifyCrisis_WhenLifted_ShouldUseLiftedType() {
        User user = new User();
        user.setId(2);
        user.setName("u");
        when(userRepository.findByIsHiddenFalse()).thenReturn(List.of(user));

        service.notifyCrisis("title", "message", AlertCode.FOOD_CRISIS_LIFTED, 70L);

        ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
        verify(notificationRepository).saveAll(captor.capture());
        assertEquals(NotificationType.FOOD_CRISIS_LIFTED, captor.getValue().get(0).getType());
    }

    @Test
    void createNotification_ShouldPersistSingleNotification() {
        User recipient = new User();
        recipient.setId(2);
        User sender = new User();
        sender.setId(1);

        Notification toSave = Notification.builder()
                .recipient(recipient)
                .sender(sender)
                .type(NotificationType.MANUAL)
                .title("title")
                .message("message")
                .referenceId(10L)
                .groupId("g1")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(toSave);

        Notification result = service.createNotification(recipient, sender, NotificationType.MANUAL,
                "title", "message", 10L, "g1");

        assertEquals(NotificationType.MANUAL, result.getType());
        verify(notificationRepository).save(any(Notification.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Notification>> notificationListCaptor() {
        return (ArgumentCaptor<List<Notification>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Specification<Notification> specAny() {
        return (Specification<Notification>) (Specification) any(Specification.class);
    }
}
