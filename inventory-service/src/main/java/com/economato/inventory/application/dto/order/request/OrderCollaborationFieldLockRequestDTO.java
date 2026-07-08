package com.economato.inventory.application.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderCollaborationFieldLockRequestDTO {

    @NotBlank(message = "fieldPath es obligatorio")
    private String fieldPath;
}
