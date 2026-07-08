package com.economato.inventory.application.dto.user.presence;

import java.time.LocalDateTime;

import com.economato.inventory.domain.model.user.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionInfo {
    private String username;
    private String displayName;
    private Role role;
    private Integer userId;
    private String sessionId;
    private String screen;
    private String screenContext;
    private LocalDateTime connectedAt;
    private LocalDateTime lastActivityAt;
    private Integer teacherId;
    private String lastAuditedScreen;
    private String lastAuditedContext;
}
