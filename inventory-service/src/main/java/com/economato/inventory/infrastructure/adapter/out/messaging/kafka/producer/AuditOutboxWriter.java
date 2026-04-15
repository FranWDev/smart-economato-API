package com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer;

import com.economato.inventory.domain.model.AuditOutbox;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AuditOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@Profile({ "!test", "kafka-test" })
@RequiredArgsConstructor
public class AuditOutboxWriter {

    private final AuditOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void saveToOutbox(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            AuditOutbox outbox = AuditOutbox.builder()
                    .topic(topic)
                    .eventKey(key)
                    .payload(payload)
                    .build();
            outboxRepository.save(outbox);
            log.debug("Evento de auditoría guardado en Outbox: topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("Excepción al guardar evento en Outbox: {}", e.getMessage(), e);
        }
    }
}
