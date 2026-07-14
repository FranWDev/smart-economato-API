package com.economato.inventory.application.usecase.notification;
import com.economato.inventory.application.dto.notification.event.PresenceAuditEvent;
import com.economato.inventory.application.dto.notification.presence.UserPresenceSnapshot;
import com.economato.inventory.application.dto.user.presence.UserSessionInfo;
import com.economato.inventory.application.usecase.order.OrderReviewLockService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Service
public class UserPresenceService {

    private static final String ADMIN_PRESENCE_TOPIC = "/topic/roles/ADMIN/presence";
    private static final String CHEF_STUDENTS_QUEUE = "/queue/student-presence";
    private static final long STALE_SECONDS = 60L;

    private final SimpMessagingTemplate messagingTemplate;
    private final AuditEventProducer auditEventProducer;
    private final SystemConfigService systemConfigService;
    private final UserRepository userRepository;
    private final OrderReviewLockService orderReviewLockService;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, UserSessionInfo>> sessionsByUser = new ConcurrentHashMap<>();

    public UserPresenceService(SimpMessagingTemplate messagingTemplate, UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.auditEventProducer = null;
        this.systemConfigService = null;
        this.orderReviewLockService = null;
    }

    public void userConnected(String username, String sessionId, Role role, Integer userId, Integer teacherId, String displayName) {
        LocalDateTime now = LocalDateTime.now();
        User resolvedUser = userRepository.findByName(username).orElse(null);

        Role effectiveRole = role != null ? role : resolvedUser != null ? resolvedUser.getRole() : null;
        Integer effectiveUserId = userId != null ? userId : resolvedUser != null ? resolvedUser.getId() : null;
        Integer effectiveTeacherId = teacherId != null ? teacherId
                : resolvedUser != null && resolvedUser.getTeacher() != null ? resolvedUser.getTeacher().getId() : null;
        String effectiveDisplayName = displayName != null ? displayName : resolvedUser != null ? resolvedUser.getName() : username;

        UserSessionInfo info = UserSessionInfo.builder()
                .username(username)
                .displayName(effectiveDisplayName)
                .role(effectiveRole)
                .userId(effectiveUserId)
                .teacherId(effectiveTeacherId)
                .sessionId(sessionId)
                .screen("DASHBOARD")
                .screenContext(null)
                .connectedAt(now)
                .lastActivityAt(now)
                .lastAuditedScreen("DASHBOARD")
                .lastAuditedContext(null)
                .build();

        sessionsByUser.computeIfAbsent(username, key -> new ConcurrentHashMap<>()).put(sessionId, info);
        publishAudit(info, "CONNECTED");
        broadcastPresence();
    }

    public void userDisconnected(String username, String sessionId) {
        ConcurrentHashMap<String, UserSessionInfo> userSessions = sessionsByUser.get(username);
        if (userSessions == null) {
            return;
        }

        UserSessionInfo removed = userSessions.remove(sessionId);
        if (removed == null) {
            return;
        }

        if (userSessions.isEmpty()) {
            sessionsByUser.remove(username);
            if (orderReviewLockService != null) {
                orderReviewLockService.releaseLocksForUser(username);
            }
        }

        publishAudit(removed, "DISCONNECTED");
        broadcastPresence();
    }

    public void updateActivity(String username, String sessionId, String screen, String context, boolean isHeartbeat) {
        ConcurrentHashMap<String, UserSessionInfo> userSessions = sessionsByUser.get(username);
        if (userSessions == null) {
            return;
        }

        UserSessionInfo info = userSessions.get(sessionId);
        if (info == null) {
            return;
        }

        String previousScreen = info.getScreen();
        String previousContext = info.getScreenContext();

        info.setScreen(screen);
        info.setScreenContext(context);
        info.setLastActivityAt(LocalDateTime.now());

        boolean stateChanged = !Objects.equals(previousScreen, screen) || !Objects.equals(previousContext, context);
        boolean differsFromLastAudited = !Objects.equals(info.getLastAuditedScreen(), screen)
                || !Objects.equals(info.getLastAuditedContext(), context);

        if (!isHeartbeat && stateChanged && differsFromLastAudited) {
            publishAudit(info, "SCREEN_CHANGED");
            info.setLastAuditedScreen(screen);
            info.setLastAuditedContext(context);
        }

        broadcastPresence();
    }

    public List<UserPresenceSnapshot> getConnectedUsers() {
        List<UserPresenceSnapshot> snapshots = new ArrayList<>();

        for (Map.Entry<String, ConcurrentHashMap<String, UserSessionInfo>> entry : sessionsByUser.entrySet()) {
            List<UserSessionInfo> tabs = new ArrayList<>(entry.getValue().values());
            if (tabs.isEmpty()) {
                continue;
            }

            tabs.sort(Comparator.comparing(UserSessionInfo::getConnectedAt));
            UserSessionInfo firstTab = tabs.get(0);

            List<UserPresenceSnapshot.TabInfo> tabInfos = tabs.stream()
                    .map(tab -> UserPresenceSnapshot.TabInfo.builder()
                            .sessionId(tab.getSessionId())
                            .screen(tab.getScreen())
                            .screenContext(tab.getScreenContext())
                            .lastActivityAt(tab.getLastActivityAt())
                            .build())
                    .toList();

            snapshots.add(UserPresenceSnapshot.builder()
                    .username(firstTab.getUsername())
                    .displayName(firstTab.getDisplayName())
                    .role(firstTab.getRole())
                    .userId(firstTab.getUserId())
                    .tabs(tabInfos)
                    .connectedSince(firstTab.getConnectedAt())
                    .build());
        }

        snapshots.sort(Comparator.comparing(UserPresenceSnapshot::getConnectedSince));
        return snapshots;
    }

    public List<UserPresenceSnapshot> getConnectedStudentsForTeacher(Integer teacherId) {
        return sessionsByUser.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(session -> Objects.equals(session.getTeacherId(), teacherId))
                .map(UserSessionInfo::getUsername)
                .distinct()
                .map(this::toSnapshot)
                .filter(Objects::nonNull)
                .toList();
    }

    @Scheduled(fixedDelay = 15000)
    public void cleanupStaleSessions() {
        long staleSeconds = STALE_SECONDS;
        if (systemConfigService != null) {
            try {
                staleSeconds = systemConfigService.getStaleSessionTimeoutSeconds();
            } catch (Exception ignored) {
                staleSeconds = STALE_SECONDS;
            }
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(staleSeconds);
        List<UserSessionInfo> stale = sessionsByUser.values().stream()
                .flatMap(sessions -> sessions.values().stream())
                .filter(session -> session.getLastActivityAt() != null && session.getLastActivityAt().isBefore(threshold))
                .toList();

        for (UserSessionInfo session : stale) {
            userDisconnected(session.getUsername(), session.getSessionId());
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = event.getUser() != null ? event.getUser().getName() : null;

        if (username != null) {
            userDisconnected(username, sessionId);
            return;
        }

        removeBySessionId(sessionId);
    }

    private void removeBySessionId(String sessionId) {
        for (Map.Entry<String, ConcurrentHashMap<String, UserSessionInfo>> entry : sessionsByUser.entrySet()) {
            if (entry.getValue().containsKey(sessionId)) {
                userDisconnected(entry.getKey(), sessionId);
                return;
            }
        }
    }

    private void broadcastPresence() {
        List<UserPresenceSnapshot> all = getConnectedUsers();
        messagingTemplate.convertAndSend(ADMIN_PRESENCE_TOPIC, all);

        Set<String> connectedChefs = sessionsByUser.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(info -> Role.CHEF.equals(info.getRole()))
                .map(UserSessionInfo::getUsername)
                .collect(Collectors.toSet());

        for (String chefUsername : connectedChefs) {
            Integer chefId = sessionsByUser.getOrDefault(chefUsername, new ConcurrentHashMap<>())
                    .values().stream()
                    .findFirst()
                    .map(UserSessionInfo::getUserId)
                    .orElse(null);

            if (chefId == null) {
                continue;
            }

            List<UserPresenceSnapshot> students = getConnectedStudentsForTeacher(chefId);
            if (!students.isEmpty()) {
                messagingTemplate.convertAndSendToUser(chefUsername, CHEF_STUDENTS_QUEUE, students);
            }
        }
    }

    private UserPresenceSnapshot toSnapshot(String username) {
        ConcurrentHashMap<String, UserSessionInfo> userSessions = sessionsByUser.get(username);
        if (userSessions == null || userSessions.isEmpty()) {
            return null;
        }

        List<UserSessionInfo> tabs = new ArrayList<>(userSessions.values());
        tabs.sort(Comparator.comparing(UserSessionInfo::getConnectedAt));

        UserSessionInfo firstTab = tabs.get(0);
        List<UserPresenceSnapshot.TabInfo> tabInfos = tabs.stream()
                .map(tab -> UserPresenceSnapshot.TabInfo.builder()
                        .sessionId(tab.getSessionId())
                        .screen(tab.getScreen())
                        .screenContext(tab.getScreenContext())
                        .lastActivityAt(tab.getLastActivityAt())
                        .build())
                .toList();

        return UserPresenceSnapshot.builder()
                .username(firstTab.getUsername())
                .displayName(firstTab.getDisplayName())
                .role(firstTab.getRole())
                .userId(firstTab.getUserId())
                .tabs(tabInfos)
                .connectedSince(firstTab.getConnectedAt())
                .build();
    }

    private void publishAudit(UserSessionInfo info, String action) {
        if (info.getUserId() == null) {
            log.debug("Skipping presence audit for username={} action={} due to missing userId", info.getUsername(), action);
            return;
        }

        if (systemConfigService != null) {
            try {
                if (!systemConfigService.isPresenceAuditEnabled()) {
                    return;
                }
            } catch (Exception ignored) {
                // fallback to default behavior
            }
        }

        PresenceAuditEvent event = PresenceAuditEvent.builder()
                .userId(info.getUserId())
                .username(info.getUsername())
                .displayName(info.getDisplayName())
                .role(info.getRole() != null ? info.getRole().name() : null)
                .screen(info.getScreen())
                .screenContext(info.getScreenContext())
                .action(action)
                .sessionId(info.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();

        if (auditEventProducer == null) {
            return;
        }

        auditEventProducer.publishPresenceAudit(event);
    }
}
