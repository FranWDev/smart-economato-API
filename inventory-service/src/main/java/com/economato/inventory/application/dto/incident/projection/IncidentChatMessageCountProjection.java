package com.economato.inventory.application.dto.incident.projection;

public interface IncidentChatMessageCountProjection {
    Long getIncidentId();

    Long getMessageCount();
}