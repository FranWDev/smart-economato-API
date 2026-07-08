package com.economato.inventory.application.dto.ai.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType;
    private Integer userId;
    private String userName;
    private Long chatId;
    private Long messageId;
    private String provider;
    private String userLanguage;
    private Integer inputTokens;
    private Integer outputTokens;
    private String toolName;
    private Long streamDurationMs;
    private Double compressionRatio;
    private String errorType;
    private LocalDateTime eventTimestamp;
}
