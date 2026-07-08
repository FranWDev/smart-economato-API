package com.economato.inventory.application.dto.shared.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogPurgeRequestDTO {
    private LocalDateTime from;
    private LocalDateTime to;
}
