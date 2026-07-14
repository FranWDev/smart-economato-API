package com.economato.inventory.application.usecase.incident;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.shared.FileStorageService;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import static org.mockito.Mockito.mock;

import com.economato.inventory.application.dto.incident.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.dto.shared.response.ChatReadReceiptBroadcastDTO;
import com.economato.inventory.application.dto.incident.response.IncidentChatTypingResponseDTO;
import com.economato.inventory.application.mapper.incident.IncidentChatReadReceiptMapper;
import com.economato.inventory.application.mapper.incident.IncidentChatMessageMapper;
import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentChatMessage;
import com.economato.inventory.domain.model.incident.IncidentChatReadReceipt;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.incident.IncidentType;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentChatMessageRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentChatReadReceiptRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident.IncidentRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentChatServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentChatMessageRepository incidentChatMessageRepository;
    @Mock
    private IncidentChatReadReceiptRepository readReceiptRepository;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private IncidentChatReadReceiptMapper readReceiptMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PersistentNotificationService persistentNotificationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private I18nService i18nService;

    private IncidentChatService service;

    private User admin;
    private User creator;
    private User relatedTeacher;
    private User nonParticipant;
    private Incident incident;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        IncidentParticipantService participantService = new IncidentParticipantService(null);
        service = new IncidentChatService(
                incidentRepository,
                incidentChatMessageRepository,
                readReceiptRepository,
                securityContextHelper,
                participantService,
                new IncidentChatMessageMapper(),
                readReceiptMapper,
                fileStorageService,
                persistentNotificationService,
                messagingTemplate,
                i18nService,
                null
        );

        admin = user(1, "Admin", "admin", Role.ADMIN, null);
        creator = user(2, "Creator", "creator", Role.ELEVATED, null);
        relatedTeacher = user(3, "Teacher", "teacher", Role.CHEF, null);
        nonParticipant = user(4, "Other", "other", Role.USER, null);
        creator.setTeacher(relatedTeacher);

        IncidentType type = IncidentType.builder().id(1).name("Type").isActive(true).build();
        incident = Incident.builder()
                .id(100L)
                .incidentType(type)
                .title("Incident")
                .description("Desc")
                .status(IncidentStatus.ABIERTO)
                .createdBy(creator)
                .relatedTeacher(relatedTeacher)
                .createdAt(LocalDateTime.now())
                .build();

        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
        lenient().when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        lenient().when(incidentChatMessageRepository.save(any(IncidentChatMessage.class))).thenAnswer(invocation -> {
            IncidentChatMessage msg = invocation.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(500L);
            }
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        lenient().when(readReceiptRepository.save(any(IncidentChatReadReceipt.class))).thenAnswer(invocation -> {
            IncidentChatReadReceipt receipt = invocation.getArgument(0);
            if (receipt.getId() == null) {
                receipt.setId(700L);
            }
            return receipt;
        });
        lenient().when(readReceiptRepository.findByIncidentIdAndUserId(any(), any())).thenReturn(Optional.empty());
        lenient().when(readReceiptRepository.findByIncidentId(any())).thenReturn(List.of());
        lenient().when(readReceiptMapper.toBroadcastDTO(any(IncidentChatReadReceipt.class), any())).thenAnswer(invocation -> {
            IncidentChatReadReceipt receipt = invocation.getArgument(0);
            Long incidentId = invocation.getArgument(1);
            return ChatReadReceiptBroadcastDTO.builder()
                    .incidentId(incidentId)
                    .userId(receipt.getUser() != null ? receipt.getUser().getId() : null)
                    .userName(receipt.getUser() != null ? receipt.getUser().getName() : null)
                    .lastReadMessageId(receipt.getLastReadMessageId())
                    .readAt(receipt.getReadAt())
                    .build();
        });
    }

    @Test
    void sendMessage_AsCreator_ShouldSaveAndBroadcast() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        IncidentChatMessageResponseDTO result = service.sendMessage(100L, "hello team", null);

        assertNotNull(result);
        assertEquals("hello team", result.getContent());
        verify(incidentChatMessageRepository).save(any(IncidentChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/100/chat"), any(IncidentChatMessageResponseDTO.class));
        verify(persistentNotificationService).notifyIncidentChatMessage(eq(incident), eq(creator));
    }

    @Test
    void sendMessage_AsAdmin_ShouldSucceed() {
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);

        IncidentChatMessageResponseDTO result = service.sendMessage(100L, "admin message", null);

        assertNotNull(result);
        assertEquals("admin message", result.getContent());
        verify(incidentChatMessageRepository).save(any(IncidentChatMessage.class));
    }

    @Test
    void sendMessage_AsRelatedTeacher_ShouldSucceed() {
        when(securityContextHelper.getCurrentUser()).thenReturn(relatedTeacher);

        IncidentChatMessageResponseDTO result = service.sendMessage(100L, "teacher message", null);

        assertNotNull(result);
        assertEquals("teacher message", result.getContent());
    }

    @Test
    void sendMessage_AsNonParticipant_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(nonParticipant);

        var thrown = assertThrows(AccessDeniedException.class, () -> service.sendMessage(100L, "not allowed", null));
        assertNotNull(thrown);

        verify(incidentChatMessageRepository, never()).save(any(IncidentChatMessage.class));
    }

    @Test
    void sendMessage_WhenIncidentClosed_ShouldThrow() {
        incident.setStatus(IncidentStatus.CERRADO_CON_RESOLUCION);
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        var thrown = assertThrows(InvalidOperationException.class, () -> service.sendMessage(100L, "should fail", null));
        assertNotNull(thrown);
    }

    @Test
    void sendMessage_EmptyContentAndNoAttachment_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        var thrown = assertThrows(InvalidOperationException.class, () -> service.sendMessage(100L, "   ", null));
        assertNotNull(thrown);

        verify(incidentChatMessageRepository, never()).save(any(IncidentChatMessage.class));
    }

    @Test
    void sendMessage_AsDeescalatedUser_ShouldSucceedIfCreator() {
        creator.setRole(Role.USER);
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        IncidentChatMessageResponseDTO result = service.sendMessage(100L, "still creator", null);

        assertNotNull(result);
        assertEquals("still creator", result.getContent());
    }

    @Test
    void markMessagesAsRead_AsParticipant_ShouldCreateReceiptAndBroadcast() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);
        when(incidentChatMessageRepository.findTopByIncidentIdOrderByIdDesc(100L))
                .thenReturn(Optional.of(chatMessage(10L, incident, creator, "last")));
        when(readReceiptRepository.findByIncidentIdAndUserId(100L, creator.getId())).thenReturn(Optional.empty());

        service.markMessagesAsRead(100L);

        verify(readReceiptRepository).save(any(IncidentChatReadReceipt.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/100/chat/read-receipts"), any(ChatReadReceiptBroadcastDTO.class));
    }

    @Test
    void markMessagesAsRead_AsParticipant_ShouldUpdateExistingReceipt() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);
        when(incidentChatMessageRepository.findTopByIncidentIdOrderByIdDesc(100L))
                .thenReturn(Optional.of(chatMessage(10L, incident, creator, "last")));

        IncidentChatReadReceipt existing = readReceipt(creator, 5L);
        when(readReceiptRepository.findByIncidentIdAndUserId(100L, creator.getId())).thenReturn(Optional.of(existing));

        service.markMessagesAsRead(100L);

        verify(readReceiptRepository).save(existing);
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/100/chat/read-receipts"), any(ChatReadReceiptBroadcastDTO.class));
        assertEquals(10L, existing.getLastReadMessageId());
    }

    @Test
    void markMessagesAsRead_WhenAlreadyUpToDate_ShouldNotBroadcast() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);
        when(incidentChatMessageRepository.findTopByIncidentIdOrderByIdDesc(100L))
                .thenReturn(Optional.of(chatMessage(10L, incident, creator, "last")));

        IncidentChatReadReceipt existing = readReceipt(creator, 10L);
        when(readReceiptRepository.findByIncidentIdAndUserId(100L, creator.getId())).thenReturn(Optional.of(existing));

        service.markMessagesAsRead(100L);

        verify(readReceiptRepository, never()).save(any(IncidentChatReadReceipt.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/incidents/100/chat/read-receipts"), any(ChatReadReceiptBroadcastDTO.class));
    }

    @Test
    void markMessagesAsRead_AsNonParticipant_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(nonParticipant);

        var thrown = assertThrows(AccessDeniedException.class, () -> service.markMessagesAsRead(100L));
        assertNotNull(thrown);

        verify(readReceiptRepository, never()).save(any(IncidentChatReadReceipt.class));
    }

    @Test
    void markMessagesAsRead_WhenNoMessages_ShouldDoNothing() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);
        when(incidentChatMessageRepository.findTopByIncidentIdOrderByIdDesc(100L)).thenReturn(Optional.empty());

        service.markMessagesAsRead(100L);

        verify(readReceiptRepository, never()).save(any(IncidentChatReadReceipt.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/incidents/100/chat/read-receipts"), any(ChatReadReceiptBroadcastDTO.class));
    }

    @Test
    void sendMessage_ShouldAutoMarkAsReadForAuthor() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);
        when(readReceiptRepository.findByIncidentIdAndUserId(100L, creator.getId())).thenReturn(Optional.empty());

        service.sendMessage(100L, "hello team", null);

        var captor = forClass(IncidentChatReadReceipt.class);
        verify(readReceiptRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getIncident().getId());
        assertEquals(creator.getId(), captor.getValue().getUser().getId());
        assertEquals(500L, captor.getValue().getLastReadMessageId());
    }

    @Test
    void broadcastTyping_AsParticipant_ShouldBroadcastTypingTrue() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        service.broadcastTyping(100L, true);

        var captor = forClass(IncidentChatTypingResponseDTO.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/incidents/100/chat/typing"), captor.capture());
        assertEquals(100L, captor.getValue().getIncidentId());
        assertEquals(creator.getId(), captor.getValue().getUserId());
        assertEquals(creator.getName(), captor.getValue().getUserName());
        assertEquals(true, captor.getValue().isTyping());
    }

    @Test
    void broadcastTyping_AsParticipant_ShouldBroadcastTypingFalse() {
        when(securityContextHelper.getCurrentUser()).thenReturn(creator);

        service.broadcastTyping(100L, false);

        var captor = forClass(IncidentChatTypingResponseDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/100/chat/typing"), captor.capture());
        assertEquals(false, captor.getValue().isTyping());
    }

    @Test
    void broadcastTyping_AsAdmin_ShouldSucceed() {
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);

        service.broadcastTyping(100L, true);

        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/100/chat/typing"), any(IncidentChatTypingResponseDTO.class));
    }

    @Test
    void broadcastTyping_AsNonParticipant_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(nonParticipant);

        var thrown = assertThrows(AccessDeniedException.class, () -> service.broadcastTyping(100L, true));
        assertNotNull(thrown);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/incidents/100/chat/typing"), any(IncidentChatTypingResponseDTO.class));
    }

    @Test
    void broadcastTyping_WhenIncidentNotFound_ShouldThrow() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        var thrown = assertThrows(ResourceNotFoundException.class,
            () -> service.broadcastTyping(999L, true));
        assertNotNull(thrown);
    }

    @Test
    void broadcastTyping_WhenUserNotAuthenticated_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(null);

        var thrown = assertThrows(AccessDeniedException.class, () -> service.broadcastTyping(100L, true));
        assertNotNull(thrown);
    }

    private User user(Integer id, String name, String username, Role role, User teacher) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setUser(username);
        u.setRole(role);
        u.setTeacher(teacher);
        return u;
    }

    private IncidentChatMessage chatMessage(Long id, Incident incident, User author, String content) {
        return IncidentChatMessage.builder()
                .id(id)
                .incident(incident)
                .author(author)
                .content(content)
                .hasAttachment(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private IncidentChatReadReceipt readReceipt(User user, Long lastReadMessageId) {
        return IncidentChatReadReceipt.builder()
                .incident(incident)
                .user(user)
                .lastReadMessageId(lastReadMessageId)
                .readAt(LocalDateTime.now())
                .build();
    }
}