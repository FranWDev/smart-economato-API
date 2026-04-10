package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.AdvancedConfigRequestDTO;
import com.economato.inventory.application.dto.response.AdvancedConfigResponseDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
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
@RequestMapping("/api/config/advanced")
@RequiredArgsConstructor
@Tag(name = "Configuración Avanzada", description = "Gestión de outbox y timeouts avanzados")
public class AdvancedConfigController {
    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración avanzada")
    public ResponseEntity<AdvancedConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(AdvancedConfigResponseDTO.builder()
                .outboxProcessingIntervalMs(c.getOutboxProcessingIntervalMs())
                .outboxBatchSize(c.getOutboxBatchSize())
                .outboxMaxConsecutiveFailures(c.getOutboxMaxConsecutiveFailures())
                .kafkaSendTimeoutSeconds(c.getKafkaSendTimeoutSeconds())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración avanzada")
    public ResponseEntity<AdvancedConfigResponseDTO> update(@Valid @RequestBody AdvancedConfigRequestDTO request,
                                                            Authentication authentication) {
        var c = systemConfigService.updateAdvancedConfig(request, authentication.getName());
        return ResponseEntity.ok(AdvancedConfigResponseDTO.builder()
                .outboxProcessingIntervalMs(c.getOutboxProcessingIntervalMs())
                .outboxBatchSize(c.getOutboxBatchSize())
                .outboxMaxConsecutiveFailures(c.getOutboxMaxConsecutiveFailures())
                .kafkaSendTimeoutSeconds(c.getKafkaSendTimeoutSeconds())
                .build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de configuración avanzada")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("ADVANCED", pageable));
    }
}
