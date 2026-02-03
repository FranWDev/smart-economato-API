package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.LoginRequestDTO;
import com.economatom.inventory.dto.request.UserRequestDTO;
import com.economatom.inventory.dto.response.LoginResponseDTO;
import com.economatom.inventory.dto.response.UserResponseDTO;
import com.economatom.inventory.service.AuthService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Endpoints para autenticación y registro de usuarios en el sistema.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario con su nombre de usuario y contraseña. Devuelve un token JWT para futuras solicitudes autenticadas.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Credenciales del usuario (nombre de usuario y contraseña)", required = true, content = @Content(schema = @Schema(implementation = LoginRequestDTO.class))), responses = {
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

    @Operation(summary = "Registrar nuevo usuario", description = "Registra un nuevo usuario en el sistema. El usuario registrado podrá autenticarse posteriormente usando su email o nombre de usuario.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del nuevo usuario (nombre, correo electrónico, contraseña, rol, etc.)", required = true, content = @Content(schema = @Schema(implementation = UserRequestDTO.class))), responses = {
            @ApiResponse(responseCode = "200", description = "Usuario registrado correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o usuario ya existente")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(
            @Valid @org.springframework.web.bind.annotation.RequestBody UserRequestDTO userRequest) {
        UserResponseDTO response = authService.register(userRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Validar token JWT", description = "Valida si el token JWT proporcionado en el header Authorization es válido y no ha expirado.", security = @SecurityRequirement(name = "bearerAuth"), responses = {
            @ApiResponse(responseCode = "200", description = "Token válido", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Token inválido o expirado")
    })
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
}
