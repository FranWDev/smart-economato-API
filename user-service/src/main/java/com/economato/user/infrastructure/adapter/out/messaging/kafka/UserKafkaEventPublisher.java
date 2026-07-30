package com.economato.user.infrastructure.adapter.out.messaging.kafka;

import com.economato.user.application.dto.event.UserCreatedEvent;
import com.economato.user.application.dto.event.UserDeletedEvent;
import com.economato.user.application.dto.event.UserRoleChangedEvent;
import com.economato.user.application.dto.event.UserUpdatedEvent;
import com.economato.user.application.port.out.UserEventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserKafkaEventPublisher implements UserEventPublisherPort {

    private static final String USER_EVENTS_TOPIC = "user-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserKafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishUserCreated(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent to topic {}: {}", USER_EVENTS_TOPIC, event);
        kafkaTemplate.send(USER_EVENTS_TOPIC, event.aggregateId(), event);
    }

    @Override
    public void publishUserUpdated(UserUpdatedEvent event) {
        log.info("Publishing UserUpdatedEvent to topic {}: {}", USER_EVENTS_TOPIC, event);
        kafkaTemplate.send(USER_EVENTS_TOPIC, event.aggregateId(), event);
    }

    @Override
    public void publishUserDeleted(UserDeletedEvent event) {
        log.info("Publishing UserDeletedEvent to topic {}: {}", USER_EVENTS_TOPIC, event);
        kafkaTemplate.send(USER_EVENTS_TOPIC, event.aggregateId(), event);
    }

    @Override
    public void publishUserRoleChanged(UserRoleChangedEvent event) {
        log.info("Publishing UserRoleChangedEvent to topic {}: {}", USER_EVENTS_TOPIC, event);
        kafkaTemplate.send(USER_EVENTS_TOPIC, event.aggregateId(), event);
    }
}
