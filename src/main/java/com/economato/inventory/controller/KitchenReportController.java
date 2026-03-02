package com.economato.inventory.controller;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.service.KitchenReportPdfService;
import com.economato.inventory.service.KitchenReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/kitchen-reports")
@Tag(name = "Reportes de Cocina", description = "Generación de reportes estadísticos basados en la auditoría de cocinado")
public class KitchenReportController {

    private final KitchenReportService service;
    private final KitchenReportPdfService pdfService;

    public KitchenReportController(KitchenReportService service, KitchenReportPdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Generar reporte de cocina", description = "Devuelve un reporte estadístico de cocina según el rango especificado. [Rol requerido: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = KitchenReportResponseDTO.class)))
    })
    public ResponseEntity<KitchenReportResponseDTO> getReport(
            @Parameter(description = "Rango del reporte (DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME, CUSTOM)", required = true) @RequestParam ReportRange range,

            @Parameter(description = "Fecha de inicio para rango CUSTOM (formato: yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Fecha de fin para rango CUSTOM (formato: yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(service.generateReport(range, startDate, endDate));
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Exportar reporte de cocina a PDF", description = "Genera y descarga un documento PDF con el reporte estadístico de cocina según el rango especificado. Si no se provee, asume ALL_TIME. [Sin restricción de rol]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "500", description = "Error al generar el PDF")
    })
    public ResponseEntity<byte[]> exportPdf(
            @Parameter(description = "Rango del reporte (DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME, CUSTOM). Por defecto ALL_TIME.") @RequestParam(required = false, defaultValue = "ALL_TIME") ReportRange range,

            @Parameter(description = "Fecha de inicio para rango CUSTOM (formato: yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Fecha de fin para rango CUSTOM (formato: yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        KitchenReportResponseDTO report = service.generateReport(range, startDate, endDate);
        byte[] pdfBytes = pdfService.generateKitchenReportPdf(report);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String filename = "reporte-cocina-" + report.getReportPeriod()
                .toLowerCase()
                .replaceAll("[^a-z0-9áéíóúüñ\\-_]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "")
                + ".pdf";
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
