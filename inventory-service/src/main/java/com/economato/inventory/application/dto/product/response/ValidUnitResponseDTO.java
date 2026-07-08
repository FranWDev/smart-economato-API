package com.economato.inventory.application.dto.product.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidUnitResponseDTO {
    private Integer id;
    private String code;
    private String category;
    private boolean active;
}
