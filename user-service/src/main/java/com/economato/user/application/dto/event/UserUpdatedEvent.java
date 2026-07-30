package com.economato.user.application.dto.event;

import java.time.Instant;

public record UserUpdatedEvent(
    String eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String version,
    UserUpdatedPayload payload
) {
    public record UserUpdatedPayload(
        Integer userId,
        String name,
        String user,
        String role,
        Integer teacherId
    ) {}
}
