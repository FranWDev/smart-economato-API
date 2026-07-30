package com.economato.user.application.dto.event;

import java.time.Instant;

public record UserCreatedEvent(
    String eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String version,
    UserCreatedPayload payload
) {
    public record UserCreatedPayload(
        Integer userId,
        String name,
        String user,
        String role,
        Integer teacherId
    ) {}
}
