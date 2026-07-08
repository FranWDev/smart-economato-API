package com.economato.inventory.application.usecase.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.economato.inventory.application.dto.notification.event.PresenceAuditEvent;
import com.economato.inventory.application.dto.notification.presence.UserPresenceSnapshot;
import com.economato.inventory.application.dto.user.presence.UserSessionInfo;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AuditEventProducer auditEventProducer;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPresenceService userPresenceService;

    private User chef;
    private User student;

    @BeforeEach
    void setUp() {
        chef = new User();
        chef.setId(10);
        chef.setName("Chef");
        chef.setUser("chefUser");
        chef.setRole(Role.CHEF);

        student = new User();
        student.setId(20);
        student.setName("Student");
        student.setUser("studentUser");
        student.setRole(Role.USER);
        student.setTeacher(chef);

        lenient().when(userRepository.findByName("chefUser")).thenReturn(Optional.of(chef));
        lenient().when(userRepository.findByName("studentUser")).thenReturn(Optional.of(student));

        try {
            Field producerField = UserPresenceService.class.getDeclaredField("auditEventProducer");
            producerField.setAccessible(true);
            producerField.set(userPresenceService, auditEventProducer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void userConnected_addsSessionToMap() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);

        List<UserPresenceSnapshot> connected = userPresenceService.getConnectedUsers();

        assertEquals(1, connected.size());
        assertEquals("studentUser", connected.get(0).getUsername());
    }

    @Test
    void userConnected_multipleTabsShowsBothSessions() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        userPresenceService.userConnected("studentUser", "s2", null, null, null, null);

        List<UserPresenceSnapshot> connected = userPresenceService.getConnectedUsers();

        assertEquals(1, connected.size());
        assertEquals(2, connected.get(0).getTabs().size());
    }

    @Test
    void userDisconnected_removesSession() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        userPresenceService.userDisconnected("studentUser", "s1");

        assertTrue(userPresenceService.getConnectedUsers().isEmpty());
    }

    @Test
    void userDisconnected_lastTab_removesUserEntirely() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        userPresenceService.userConnected("studentUser", "s2", null, null, null, null);

        userPresenceService.userDisconnected("studentUser", "s1");
        assertEquals(1, userPresenceService.getConnectedUsers().get(0).getTabs().size());

        userPresenceService.userDisconnected("studentUser", "s2");
        assertTrue(userPresenceService.getConnectedUsers().isEmpty());
    }

    @Test
    void updateActivity_changesScreenAndPublishesAudit() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        clearInvocations(auditEventProducer);

        userPresenceService.updateActivity("studentUser", "s1", "ORDER_RECEPTION", "Orden #6", false);

        ArgumentCaptor<PresenceAuditEvent> captor = ArgumentCaptor.forClass(PresenceAuditEvent.class);
        verify(auditEventProducer).publishPresenceAudit(captor.capture());
        assertEquals("SCREEN_CHANGED", captor.getValue().getAction());
    }

    @Test
    void updateActivity_heartbeatDoesNotPublishAudit() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        clearInvocations(auditEventProducer);

        userPresenceService.updateActivity("studentUser", "s1", "DASHBOARD", null, true);

        verify(auditEventProducer, never()).publishPresenceAudit(any());
    }

    @Test
    void updateActivity_sameScreenDoesNotPublishAudit() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        clearInvocations(auditEventProducer);

        userPresenceService.updateActivity("studentUser", "s1", "DASHBOARD", null, false);

        verify(auditEventProducer, never()).publishPresenceAudit(any());
    }

    @Test
    void updateActivity_sameScreenDifferentContextPublishesAudit() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        userPresenceService.updateActivity("studentUser", "s1", "ORDER_RECEPTION", "Orden #6", false);
        clearInvocations(auditEventProducer);

        userPresenceService.updateActivity("studentUser", "s1", "ORDER_RECEPTION", "Orden #7", false);

        verify(auditEventProducer).publishPresenceAudit(any(PresenceAuditEvent.class));
    }

    @Test
    void updateActivity_updatesLastActivityAt() throws Exception {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        LocalDateTime before = getSession("studentUser", "s1").getLastActivityAt();

        userPresenceService.updateActivity("studentUser", "s1", "DASHBOARD", null, true);

        LocalDateTime after = getSession("studentUser", "s1").getLastActivityAt();
        assertNotNull(after);
        assertTrue(after.isAfter(before) || after.isEqual(before));
    }

    @Test
    void staleSessionCleanup_removesInactiveSessions() throws Exception {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        setLastActivity("studentUser", "s1", LocalDateTime.now().minusSeconds(70));
        clearInvocations(auditEventProducer);

        userPresenceService.cleanupStaleSessions();

        assertTrue(userPresenceService.getConnectedUsers().isEmpty());
        ArgumentCaptor<PresenceAuditEvent> captor = ArgumentCaptor.forClass(PresenceAuditEvent.class);
        verify(auditEventProducer).publishPresenceAudit(captor.capture());
        assertEquals("DISCONNECTED", captor.getValue().getAction());
    }

    @Test
    void broadcastPresence_sendsToAdminTopic() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/roles/ADMIN/presence"), any(Object.class));
    }

    @Test
    void broadcastPresence_sendsToChefQueue() {
        userPresenceService.userConnected("chefUser", "chef-s1", null, null, null, null);
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);

        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(eq("chefUser"), eq("/queue/student-presence"), any());
    }

    @Test
    void getConnectedStudentsForTeacher_filtersCorrectly() {
        User otherChef = new User();
        otherChef.setId(99);
        otherChef.setRole(Role.CHEF);
        User otherStudent = new User();
        otherStudent.setId(30);
        otherStudent.setName("Other Student");
        otherStudent.setUser("otherStudent");
        otherStudent.setRole(Role.USER);
        otherStudent.setTeacher(otherChef);

        when(userRepository.findByName("otherStudent")).thenReturn(Optional.of(otherStudent));

        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        userPresenceService.userConnected("otherStudent", "s2", null, null, null, null);

        List<UserPresenceSnapshot> forChef = userPresenceService.getConnectedStudentsForTeacher(10);

        assertEquals(1, forChef.size());
        assertEquals("studentUser", forChef.get(0).getUsername());
    }

    @Test
    void userConnected_publishesConnectedAuditEvent() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);

        ArgumentCaptor<PresenceAuditEvent> captor = ArgumentCaptor.forClass(PresenceAuditEvent.class);
        verify(auditEventProducer).publishPresenceAudit(captor.capture());
        assertEquals("CONNECTED", captor.getValue().getAction());
    }

    @Test
    void userDisconnected_publishesDisconnectedAuditEvent() {
        userPresenceService.userConnected("studentUser", "s1", null, null, null, null);
        clearInvocations(auditEventProducer);

        userPresenceService.userDisconnected("studentUser", "s1");

        ArgumentCaptor<PresenceAuditEvent> captor = ArgumentCaptor.forClass(PresenceAuditEvent.class);
        verify(auditEventProducer).publishPresenceAudit(captor.capture());
        assertEquals("DISCONNECTED", captor.getValue().getAction());
    }

    @SuppressWarnings("unchecked")
    private UserSessionInfo getSession(String username, String sessionId) throws Exception {
        Field field = UserPresenceService.class.getDeclaredField("sessionsByUser");
        field.setAccessible(true);
        Map<String, ConcurrentHashMap<String, UserSessionInfo>> map =
                (Map<String, ConcurrentHashMap<String, UserSessionInfo>>) field.get(userPresenceService);
        return map.get(username).get(sessionId);
    }

    private void setLastActivity(String username, String sessionId, LocalDateTime time) throws Exception {
        UserSessionInfo session = getSession(username, sessionId);
        assertFalse(session == null);
        session.setLastActivityAt(time);
    }
}
