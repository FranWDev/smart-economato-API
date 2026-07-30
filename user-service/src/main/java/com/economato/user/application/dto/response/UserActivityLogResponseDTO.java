package com.economato.user.application.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogResponseDTO {
    private Long id;
    private Integer userId;
    private String username;
    private String displayName;
    private String action;
    private String screen;
    private String screenContext;
    private String sessionId;
    private LocalDateTime timestamp;
}
