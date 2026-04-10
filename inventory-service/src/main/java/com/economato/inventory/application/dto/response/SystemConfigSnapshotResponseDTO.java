package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigSnapshotResponseDTO {
    private PresenceConfigResponseDTO presence;
    private AlertsConfigResponseDTO alerts;
    private PredictionsConfigResponseDTO predictions;
    private SessionsConfigResponseDTO sessions;
    private SecurityConfigResponseDTO security;
    private IncidentsConfigResponseDTO incidents;
    private NotificationsConfigResponseDTO notifications;
    private AdvancedConfigResponseDTO advanced;

    private String updatedBy;
    private LocalDateTime updatedAt;
}
