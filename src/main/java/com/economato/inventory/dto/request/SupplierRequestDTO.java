package com.economato.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un proveedor.")
public class SupplierRequestDTO {

    @JsonProperty("name")
    @NotBlank(message = "{validation.supplierRequestDTO.name.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.supplierRequestDTO.name.size}")
    @Schema(description = "Nombre del proveedor", example = "Mariscos Recio S. L.")
    private String name;

    @JsonProperty("email")
    @Schema(description = "Correo electrónico del proveedor", example = "contacto@mariscosrecio.com")
    private String email;

    @JsonProperty("phone")
    @Schema(description = "Número de teléfono del proveedor", example = "+34600123456")
    private String phone;
}
