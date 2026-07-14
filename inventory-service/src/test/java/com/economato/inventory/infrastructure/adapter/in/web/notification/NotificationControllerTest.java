package com.economato.inventory.infrastructure.adapter.in.web.notification;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;

import com.economato.inventory.application.dto.notification.request.SendNotificationRequestDTO;
import com.economato.inventory.application.dto.notification.response.NotificationResponseDTO;
import com.economato.inventory.application.dto.notification.response.NotificationUnreadCountDTO;
import com.economato.inventory.application.mapper.notification.NotificationMapper;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.RoleNotificationService;
import com.economato.inventory.domain.model.notification.Notification;
import com.economato.inventory.domain.model.notification.NotificationType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.notification.NotificationRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import org.springframework.beans.factory.ObjectProvider;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private RoleNotificationService roleNotificationService;

    @Mock
    private I18nService i18nService;

    @Mock
    private ObjectProvider<SystemConfigService> systemConfigServiceProvider;

    private PersistentNotificationService persistentNotificationService;
    private NotificationController controller;

    private User currentUser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        persistentNotificationService = new PersistentNotificationService(
                notificationRepository,
                notificationMapper,
                userRepository,
                securityContextHelper,
                roleNotificationService,
                i18nService,
                systemConfigServiceProvider
        );
        controller = new NotificationController(persistentNotificationService);

        currentUser = new User();
        currentUser.setId(1);
        currentUser.setName("current");
        currentUser.setRole(Role.ADMIN);

        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void getMyNotifications_ShouldDelegateToService() {
        Notification entity = Notification.builder().id(1L).recipient(currentUser).title("t").message("m").type(NotificationType.MANUAL).build();
        NotificationResponseDTO dto = NotificationResponseDTO.builder().id(1L).build();
        Page<Notification> page = new PageImpl<>(List.of(entity));
        PageRequest pageable = PageRequest.of(0, 10);
        when(notificationRepository.findAll(specAny(), any(Pageable.class))).thenReturn(page);
        when(notificationMapper.toResponseDTO(entity)).thenReturn(dto);

        ResponseEntity<Page<NotificationResponseDTO>> response = controller.getMyNotifications(
                NotificationType.MANUAL,
                false,
                null,
                null,
                pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        verify(notificationRepository).findAll(specAny(), any(Pageable.class));
    }

    @Test
    void getUnreadCount_ShouldDelegateToService() {
        when(notificationRepository.countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(1)).thenReturn(4L);

        ResponseEntity<NotificationUnreadCountDTO> response = controller.getUnreadCount();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4, response.getBody().getCount());
        verify(notificationRepository).countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(1);
    }

    @Test
    void markAsRead_ShouldDelegateToService() {
        Notification n = Notification.builder().id(12L).recipient(currentUser).isRead(false).build();
        when(notificationRepository.findByIdAndRecipientId(12L, 1)).thenReturn(Optional.of(n));

        ResponseEntity<Void> response = controller.markAsRead(12L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationRepository).save(n);
        assertTrue(n.isRead());
    }

    @Test
    void markAllAsRead_ShouldDelegateToService() {
        ResponseEntity<Void> response = controller.markAllAsRead();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationRepository).markAllAsReadByRecipientId(1);
    }

    @Test
    void deleteNotification_ShouldDelegateToService() {
        Notification n = Notification.builder().id(20L).recipient(currentUser).isDeletedByRecipient(false).build();
        when(notificationRepository.findByIdAndRecipientId(20L, 1)).thenReturn(Optional.of(n));

        ResponseEntity<Void> response = controller.deleteNotification(20L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationRepository).save(n);
        assertTrue(n.isDeletedByRecipient());
    }

    @Test
    void deleteManualNotificationGroup_ShouldDelegateToService() {
        ResponseEntity<Void> response = controller.deleteManualNotificationGroup("group-uuid");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationRepository).softDeleteManualGroupByGroupIdAndSenderId("group-uuid", 1);
    }

    @Test
    void sendManualNotification_ShouldDelegateToService() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO("title", "message", List.of(1, 2), null);
        User u1 = new User();
        u1.setId(1);
        u1.setName("u1");
        User u2 = new User();
        u2.setId(2);
        u2.setName("u2");
        when(userRepository.findAllById(List.of(1, 2))).thenReturn(List.of(u1, u2));

        ResponseEntity<Void> response = controller.sendManualNotification(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationRepository).saveAll(any());
    }

    @Test
    void sendToRole_ShouldBuildRequestAndDelegate() {
        User recipient = new User();
        recipient.setId(5);
        recipient.setName("role-user");
        when(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN)).thenReturn(List.of(recipient));

        ResponseEntity<Void> response = controller.sendToRole(Role.ADMIN, "Role title", "Role message");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(notificationRepository).saveAll(any());
        verify(roleNotificationService).sendNotificationToUser("role-user", "Role title", "Role message");
    }

    @Test
    void sendToUser_WhenFoundByName_ShouldDelegateWithRecipientId() {
        User user = new User();
        user.setId(42);
        user.setName("john");
        when(userRepository.findByName("john")).thenReturn(Optional.of(user));
        when(userRepository.findAllById(List.of(42))).thenReturn(List.of(user));

        ResponseEntity<Void> response = controller.sendToUser("john", "User title", "User message");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationRepository).saveAll(any());
        verify(roleNotificationService).sendNotificationToUser("john", "User title", "User message");
    }

    @Test
    void sendToUser_WhenNotFoundByName_ShouldFallbackToUserField() {
        User user = new User();
        user.setId(7);
        user.setUser("legacy-user");
        user.setName("legacy-user");

        when(userRepository.findByName("legacy-user")).thenReturn(Optional.empty());
        when(userRepository.findByUser("legacy-user")).thenReturn(Optional.of(user));
        when(userRepository.findAllById(List.of(7))).thenReturn(List.of(user));

        ResponseEntity<Void> response = controller.sendToUser("legacy-user", "t", "m");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findByName("legacy-user");
        verify(userRepository).findByUser("legacy-user");
        verify(notificationRepository).saveAll(any());
    }

    @Test
    void sendToUser_WhenUserNotFound_ShouldThrowResourceNotFound() {
        when(userRepository.findByName("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUser("ghost")).thenReturn(Optional.empty());
        when(i18nService.getMessage(eq(MessageKey.ERROR_USER_NOT_FOUND), any(Object[].class)))
                .thenReturn("User not found: ghost");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> controller.sendToUser("ghost", "t", "m"));

        assertEquals("User not found: ghost", ex.getMessage());
        verify(notificationRepository, never()).saveAll(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Specification<Notification> specAny() {
        return (Specification<Notification>) (Specification) any(Specification.class);
    }
}
