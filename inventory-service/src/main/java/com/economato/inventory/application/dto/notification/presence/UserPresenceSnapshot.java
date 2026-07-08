package com.economato.inventory.application.dto.notification.presence;

import java.time.LocalDateTime;
import java.util.List;

import com.economato.inventory.domain.model.user.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresenceSnapshot {
    private String username;
    private String displayName;
    private Role role;
    private Integer userId;
    private List<TabInfo> tabs;
    private LocalDateTime connectedSince;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TabInfo {
        private String sessionId;
        private String screen;
        private String screenContext;
        private LocalDateTime lastActivityAt;
    }
}
