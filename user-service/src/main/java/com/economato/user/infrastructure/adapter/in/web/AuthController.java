package com.economato.user.infrastructure.adapter.in.web;

import com.economato.user.application.dto.request.LoginRequestDTO;
import com.economato.user.application.dto.response.LoginResponseDTO;
import com.economato.user.application.port.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
@Tag(name = "Autenticación", description = "Endpoints para autenticación y sesiones de usuario")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario y devuelve el token JWT")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return ResponseEntity.ok(authUseCase.login(loginRequest));
    }

    @Operation(summary = "Validar token JWT")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(Authentication authentication) {
        return ResponseEntity.ok(authUseCase.validateToken(authentication));
    }

    @Operation(summary = "Obtener rol del token")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/role")
    public ResponseEntity<Map<String, String>> getUserRole(Authentication authentication) {
        return ResponseEntity.ok(authUseCase.getUserRole(authentication));
    }

    @Operation(summary = "Cerrar sesión")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(authUseCase.logoutWithHeader(authHeader));
    }
}
