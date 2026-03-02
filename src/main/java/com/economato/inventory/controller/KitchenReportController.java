package com.economato.inventory.controller;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.service.KitchenReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/kitchen-reports")
@Tag(name = "Reportes de Cocina", description = "Generación de reportes estadísticos basados en la auditoría de cocinado")
public class KitchenReportController {

    private final KitchenReportService service;

    public KitchenReportController(KitchenReportService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Generar reporte de cocina", 
               description = "Devuelve un reporte estadístico de cocina según el rango especificado. [Rol requerido: ADMIN]")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado exitosamente",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = KitchenReportResponseDTO.class)))
    })
    public ResponseEntity<KitchenReportResponseDTO> getReport(
            @Parameter(description = "Rango del reporte (DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME, CUSTOM)", required = true)
            @RequestParam ReportRange range,
            
            @Parameter(description = "Fecha de inicio para rango CUSTOM (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "Fecha de fin para rango CUSTOM (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
            
        return ResponseEntity.ok(service.generateReport(range, startDate, endDate));
    }
}
