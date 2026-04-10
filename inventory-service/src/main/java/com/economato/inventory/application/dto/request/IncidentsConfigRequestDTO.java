package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentsConfigRequestDTO {

    @NotNull @Min(100) @Max(50000)
    private Integer maxChatMessageLength;

    @NotNull @Min(1) @Max(1000)
    private Integer maxAdminAttachableAudits;

    @NotNull @Min(1048576) @Max(104857600)
    private Long maxUploadFileSizeBytes;

    @NotBlank
    private String allowedFileTypes;
}
