package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.AlertsConfigRequestDTO;
import com.economato.inventory.application.dto.response.AlertsConfigResponseDTO;
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
@RequestMapping("/api/config/alerts")
@RequiredArgsConstructor
@Tag(name = "Configuración de Alertas", description = "Gestión de umbrales de alertas y caducidad")
public class AlertsConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de alertas")
    public ResponseEntity<AlertsConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(AlertsConfigResponseDTO.builder()
                .alertThresholdOkDays(c.getAlertThresholdOkDays())
                .alertThresholdLowDays(c.getAlertThresholdLowDays())
                .alertThresholdMediumDays(c.getAlertThresholdMediumDays())
                .alertThresholdHighDays(c.getAlertThresholdHighDays())
                .expirationCriticalDays(c.getExpirationCriticalDays())
                .expirationHighDays(c.getExpirationHighDays())
                .expirationMediumDays(c.getExpirationMediumDays())
                .forecastHorizonDays(c.getForecastHorizonDays())
                .forecastHistoryWeeks(c.getForecastHistoryWeeks())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de alertas")
    public ResponseEntity<AlertsConfigResponseDTO> update(@Valid @RequestBody AlertsConfigRequestDTO request,
                                                          Authentication authentication) {
        var c = systemConfigService.updateAlertsConfig(request, authentication.getName());
        return ResponseEntity.ok(AlertsConfigResponseDTO.builder()
                .alertThresholdOkDays(c.getAlertThresholdOkDays())
                .alertThresholdLowDays(c.getAlertThresholdLowDays())
                .alertThresholdMediumDays(c.getAlertThresholdMediumDays())
                .alertThresholdHighDays(c.getAlertThresholdHighDays())
                .expirationCriticalDays(c.getExpirationCriticalDays())
                .expirationHighDays(c.getExpirationHighDays())
                .expirationMediumDays(c.getExpirationMediumDays())
                .forecastHorizonDays(c.getForecastHorizonDays())
                .forecastHistoryWeeks(c.getForecastHistoryWeeks())
                .build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de alertas")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategoryDto("ALERTS", pageable));
    }
}
