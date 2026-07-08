package com.economato.inventory.application.dto.shared.response;
import com.economato.inventory.application.dto.incident.response.IncidentsConfigResponseDTO;
import com.economato.inventory.application.dto.notification.response.NotificationsConfigResponseDTO;
import com.economato.inventory.application.dto.notification.response.PresenceConfigResponseDTO;
import com.economato.inventory.application.dto.stock.response.AlertsConfigResponseDTO;

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
