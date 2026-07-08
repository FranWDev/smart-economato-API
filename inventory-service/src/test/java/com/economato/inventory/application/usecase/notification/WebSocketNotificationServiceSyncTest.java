package com.economato.inventory.application.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.economato.inventory.application.dto.shared.event.RealtimeSyncEvent;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationServiceSyncTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private I18nService i18nService;

    @Mock
    private UserPresenceService userPresenceService;

    @InjectMocks
    private WebSocketNotificationService service;

    private RealtimeSyncEvent event;

    @BeforeEach
    void setUp() {
        event = RealtimeSyncEvent.builder()
                .entityType("product")
                .entityId(42)
                .action("UPDATE")
                .affectedDomains(List.of("product", "recipe", "weekly_plan"))
                .changedBy("chef.test")
                .timestamp(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: Envía al topic correcto
    // -------------------------------------------------------------------------

    @Test
    void broadcastSync_sendsToCorrectTopic() {
        service.broadcastSync(event);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sync"), captor.capture());
        assertThat(captor.getValue().getEntityType()).isEqualTo("product");
        assertThat(captor.getValue().getEntityId()).isEqualTo(42);
        assertThat(captor.getValue().getAffectedDomains()).containsExactly("product", "recipe", "weekly_plan");
    }

    // -------------------------------------------------------------------------
    // Test 2: No propaga excepción si el messaging falla
    // -------------------------------------------------------------------------

    @Test
    void broadcastSync_doesNotThrow_whenMessagingFails() {
        doThrow(new RuntimeException("Broker down")).when(messagingTemplate)
                .convertAndSend(eq("/topic/sync"), (Object) org.mockito.ArgumentMatchers.any());

        // No debe lanzar
        service.broadcastSync(event);
        // Si llegamos aquí, el test pasa
    }

    // -------------------------------------------------------------------------
    // Test 3: Si timestamp es null, se autocompleta antes de enviar
    // -------------------------------------------------------------------------

    @Test
    void broadcastSync_setsTimestamp_ifNull() {
        event.setTimestamp(null);

        service.broadcastSync(event);

        ArgumentCaptor<RealtimeSyncEvent> captor = ArgumentCaptor.forClass(RealtimeSyncEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sync"), captor.capture());
        assertThat(captor.getValue().getTimestamp()).isNotNull();
    }
}
