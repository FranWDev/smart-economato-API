package com.economato.inventory.application.dto.notification.event;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceAuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer userId;
    private String username;
    private String displayName;
    private String role;
    private String screen;
    private String screenContext;
    private String action;
    private String sessionId;
    private LocalDateTime timestamp;
}
