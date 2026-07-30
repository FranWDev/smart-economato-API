package com.economato.user.application.dto.event;

import java.time.Instant;

public record UserDeletedEvent(
    String eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String version,
    UserDeletedPayload payload
) {
    public record UserDeletedPayload(
        Integer userId,
        String user
    ) {}
}
