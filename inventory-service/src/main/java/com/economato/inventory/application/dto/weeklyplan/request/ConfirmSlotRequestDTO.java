package com.economato.inventory.application.dto.weeklyplan.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmSlotRequestDTO {
    private String notes;
}
