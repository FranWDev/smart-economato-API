package com.economato.inventory.application.dto.product.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidUnitRequestDTO {

    @NotBlank
    @Size(max = 30)
    private String code;

    @NotBlank
    @Pattern(regexp = "PESO|VOLUMEN|COCINA|DISCRETA|ENVASE|FORMA")
    private String category;

    private Boolean active;
}
