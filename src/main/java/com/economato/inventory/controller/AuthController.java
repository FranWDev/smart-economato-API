package com.economato.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Endpoints para autenticación y registro de usuarios en el sistema.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario con su nombre de usuario y contraseña. Devuelve un token JWT para futuras solicitudes autenticadas. [Acceso público]", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Credenciales del usuario (nombre de usuario y contraseña)", required = true, content = @Content(schema = @Schema(implementation = LoginRequestDTO.class))), responses = {
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa, se devuelve el token JWT", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Credenciales inválidas o datos incompletos"),
            @ApiResponse(responseCode = "401", description = "Usuario no autorizado")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> authenticateUser(
            @Valid @org.springframework.web.bind.annotation.RequestBody LoginRequestDTO loginRequest) {
        LoginResponseDTO response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Validar token JWT", description = "Valida si el token JWT proporcionado en el header Authorization es válido y no ha expirado. [Rol requerido: USER]", security = @SecurityRequirement(name = "bearerAuth"), responses = {
            @ApiResponse(responseCode = "200", description = "Token válido", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Token inválido o expirado")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            response.put("valid", true);
            response.put("username", authentication.getName());
            return ResponseEntity.ok(response);
        }

        response.put("valid", false);
        return ResponseEntity.status(401).body(response);
    }
    
    @Operation(summary = "Obtener rol del token", description = "Devuelve el rol del usuario autenticado a partir del token JWT. [Rol requerido: USER]", security = @SecurityRequirement(name = "bearerAuth"), responses = {
            @ApiResponse(responseCode = "200", description = "Rol obtenido correctamente", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/role")
    public ResponseEntity<Map<String, String>> getUserRole(Authentication authentication) {
        Map<String, String> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                    .orElse("USER");
            response.put("role", role);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401).body(response);
    }

    @Operation(summary = "Cerrar sesión", description = "Invalida el token JWT del usuario actual, añadiéndolo a la lista negra para que no pueda ser usado nuevamente. [Rol requerido: USER]", security = @SecurityRequirement(name = "bearerAuth"), responses = {
            @ApiResponse(responseCode = "200", description = "Sesión cerrada exitosamente", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Token no proporcionado o inválido"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, String> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("message", "Token no proporcionado");
            return ResponseEntity.badRequest().body(response);
        }

        String token = authHeader.substring(7);
        
        try {
            authService.logout(token);
            response.put("message", "Sesión cerrada exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("message", "Error al cerrar sesión: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
