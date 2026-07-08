package com.economato.inventory.infrastructure.adapter.in.web.shared.config;

import com.economato.inventory.application.dto.shared.request.SecurityConfigRequestDTO;
import com.economato.inventory.application.dto.shared.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.shared.response.SecurityConfigResponseDTO;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
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
@RequestMapping("/api/config/security")
@RequiredArgsConstructor
@Tag(name = "Configuración de Seguridad", description = "Gestión de JWT y políticas de seguridad")
public class SecurityConfigController {
    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de seguridad")
    public ResponseEntity<SecurityConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(SecurityConfigResponseDTO.builder()
                .jwtExpirationMs(c.getJwtExpirationMs())
                .minPasswordLength(c.getMinPasswordLength())
                .maxEscalationMinutes(c.getMaxEscalationMinutes())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de seguridad")
    public ResponseEntity<SecurityConfigResponseDTO> update(@Valid @RequestBody SecurityConfigRequestDTO request,
                                                            Authentication authentication) {
        var c = systemConfigService.updateSecurityConfig(request, authentication.getName());
        return ResponseEntity.ok(SecurityConfigResponseDTO.builder()
                .jwtExpirationMs(c.getJwtExpirationMs())
                .minPasswordLength(c.getMinPasswordLength())
                .maxEscalationMinutes(c.getMaxEscalationMinutes())
                .build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de seguridad")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("SECURITY", pageable));
    }
}
