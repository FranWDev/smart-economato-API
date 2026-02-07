package com.economato.inventory.dto.response;

import com.economato.inventory.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta tras la autenticación exitosa, contiene el token JWT y el rol del usuario.")
public class LoginResponseDTO {

    @Schema(description = "Token JWT generado tras la autenticación", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
    
    @Schema(description = "Rol del usuario autenticado", example = "USER")
    private Role role;
}
