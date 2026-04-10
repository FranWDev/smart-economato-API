package com.economato.inventory.infrastructure.adapter.in.web.config;

import com.economato.inventory.application.dto.request.PredictionsConfigRequestDTO;
import com.economato.inventory.application.dto.response.ConfigAuditLogResponseDTO;
import com.economato.inventory.application.dto.response.PredictionsConfigResponseDTO;
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
@RequestMapping("/api/config/predictions")
@RequiredArgsConstructor
@Tag(name = "Configuración de Predicciones", description = "Gestión de refresco y parámetros de predicción")
public class PredictionsConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener configuración de predicciones")
    public ResponseEntity<PredictionsConfigResponseDTO> get() {
        var c = systemConfigService.getConfigEntity();
        return ResponseEntity.ok(PredictionsConfigResponseDTO.builder()
                .predictionRefreshEnabled(c.isPredictionRefreshEnabled())
                .predictionRefreshIntervalHours(c.getPredictionRefreshIntervalHours())
                .predictionHistoryDays(c.getPredictionHistoryDays())
                .predictionBatchSize(c.getPredictionBatchSize())
                .build());
    }

    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar configuración de predicciones")
    public ResponseEntity<PredictionsConfigResponseDTO> update(@Valid @RequestBody PredictionsConfigRequestDTO request,
                                                               Authentication authentication) {
        var c = systemConfigService.updatePredictionsConfig(request, authentication.getName());
        return ResponseEntity.ok(PredictionsConfigResponseDTO.builder()
                .predictionRefreshEnabled(c.isPredictionRefreshEnabled())
                .predictionRefreshIntervalHours(c.getPredictionRefreshIntervalHours())
                .predictionHistoryDays(c.getPredictionHistoryDays())
                .predictionBatchSize(c.getPredictionBatchSize())
                .build());
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de cambios de predicciones")
    public ResponseEntity<Page<ConfigAuditLogResponseDTO>> auditLog(Pageable pageable) {
        return ResponseEntity.ok(systemConfigService.getAuditByCategory("PREDICTIONS", pageable)
                .map(log -> ConfigAuditLogResponseDTO.builder()
                        .username(log.getUser() != null ? log.getUser().getName() : null)
                        .category(log.getCategory())
                        .fieldName(log.getFieldName())
                        .oldValue(log.getOldValue())
                        .newValue(log.getNewValue())
                        .changedAt(log.getChangedAt())
                        .build()));
    }
}
