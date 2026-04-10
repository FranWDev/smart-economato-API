package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.SessionsConfigRequestDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.response.SessionsConfigResponseDTO;
import com.economato.inventory.application.usecase.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/sessions")
@RequiredArgsConstructor
@Tag(name = "Configuración de Sesiones", description = "Gestión de timeout de sesiones de presencia")
public class SessionsConfigController {
    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de sesiones")
    public ResponseEntity<SessionsConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(SessionsConfigResponseDTO.builder().staleSessionTimeoutSeconds(c.getStaleSessionTimeoutSeconds()).build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de sesiones")
    public ResponseEntity<SessionsConfigResponseDTO> update(@Valid @RequestBody SessionsConfigRequestDTO request,
                                                            Authentication authentication) {
        var c = systemConfigService.updateSessionsConfig(request, authentication.getName());
        return ResponseEntity.ok(SessionsConfigResponseDTO.builder().staleSessionTimeoutSeconds(c.getStaleSessionTimeoutSeconds()).build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de sesiones")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("SESSIONS", pageable));
    }
}
