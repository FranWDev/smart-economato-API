package com.economato.inventory.application.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachAuditRequestDTO {

    @NotEmpty(message = "{validation.attachAuditRequestDTO.cookingAuditIds.notEmpty}")
    private List<Long> cookingAuditIds;
}
