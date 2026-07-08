package com.economato.inventory.infrastructure.adapter.in.web.crisis;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;

import com.economato.inventory.application.dto.crisis.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.crisis.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.crisis.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.crisis.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.crisis.response.ReverseTraceabilityDTO;
import com.economato.inventory.application.usecase.crisis.TraceabilityService;
import com.economato.inventory.infrastructure.adapter.out.external.crisis.reports.CrisisReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traceability")
@RequiredArgsConstructor
@Tag(name = "Trazabilidad", description = "Endpoints para trazabilidad alimentaria y gestión de crisis")
public class TraceabilityController {

    private final TraceabilityService traceabilityService;
    private final CrisisReportPdfService crisisReportPdfService;

    @PostMapping("/crisis/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar una crisis de seguridad alimentaria", description = "Bloquea productos afectados, rastrea pedidos y cocinados, y notifica a todos los usuarios.")
    public ResponseEntity<CrisisResponseDTO> activateCrisis(@Valid @RequestBody CrisisActivationRequestDTO request) {
        return ResponseEntity.ok(traceabilityService.activateCrisis(request));
    }

    @PostMapping("/crisis/lift")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Levantar una cuarentena alimentaria", description = "Levanta la crisis por ID, restaura disponibilidad y registra la acción en el ledger.")
    public ResponseEntity<Void> liftCrisis(@Valid @RequestBody CrisisLiftRequestDTO request) {
        traceabilityService.liftCrisis(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/crisis")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener todas las crisis registradas")
    public ResponseEntity<List<CrisisResponseDTO>> getAllCrises() {
        return ResponseEntity.ok(traceabilityService.getAllCrises());
    }

    @GetMapping("/crisis/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener historial de crisis (paginado y filtrado)")
    public ResponseEntity<Page<CrisisResponseDTO>> getCrisisHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "activatedAt"));
        return ResponseEntity.ok(traceabilityService.getCrisisHistory(search, pageable));
    }

    @GetMapping("/crisis/{crisisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener una crisis por ID")
    public ResponseEntity<CrisisResponseDTO> getCrisisById(@PathVariable Long crisisId) {
        return ResponseEntity.ok(traceabilityService.getCrisisById(crisisId));
    }

    @GetMapping("/forward")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener reporte de trazabilidad hacia adelante")
    public ResponseEntity<ForwardTraceabilityDTO> getForwardTraceability(
            @Parameter(description = "ID del proveedor") @RequestParam Integer supplierId,
            @Parameter(description = "Lista de IDs de productos") @RequestParam List<Integer> productIds,
            @Parameter(description = "Fecha desde") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Fecha hasta") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(traceabilityService.getForwardTraceability(supplierId, productIds, from, to));
    }

    @GetMapping("/reverse/{cookingAuditId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener reporte de trazabilidad inversa")
    public ResponseEntity<ReverseTraceabilityDTO> getReverseTraceability(
            @Parameter(description = "ID de la auditoría de cocinado") @PathVariable Long cookingAuditId) {
        return ResponseEntity.ok(traceabilityService.getReverseTraceability(cookingAuditId));
    }

    @GetMapping("/crisis/{crisisId}/report/download")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Descargar reporte de crisis en PDF")
    public ResponseEntity<byte[]> downloadCrisisReport(@PathVariable Long crisisId) {
        CrisisResponseDTO crisisData = traceabilityService.getCrisisById(crisisId);
        byte[] pdf = crisisReportPdfService.generateCrisisReport(crisisData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "crisis_report_" + crisisData.getCrisisCode() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/batch/{batchId}/cookings")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF', 'ELEVATED')")
    @Operation(summary = "Obtener los cocinados asociados a un lote específico")
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> getCookingAuditsByBatchId(
            @Parameter(description = "ID del lote") @PathVariable Long batchId) {
        return ResponseEntity.ok(traceabilityService.getCookingAuditsByBatchId(batchId));
    }
}