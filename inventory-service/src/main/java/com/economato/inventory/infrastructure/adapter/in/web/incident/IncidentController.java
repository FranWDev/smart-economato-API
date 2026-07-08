package com.economato.inventory.infrastructure.adapter.in.web.incident;
import com.economato.inventory.application.dto.incident.request.CloseIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.OpenIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.request.RevertAuditFromIncidentRequestDTO;
import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentListResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.dto.shared.request.AttachAuditRequestDTO;



import com.economato.inventory.application.usecase.incident.IncidentChatService;
import com.economato.inventory.application.usecase.incident.IncidentService;
import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.infrastructure.adapter.out.external.incident.reports.IncidentReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidencias", description = "Gestión integral de incidencias")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentChatService incidentChatService;
    private final IncidentReportPdfService incidentReportPdfService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Crear incidencia")
    public ResponseEntity<IncidentResponseDTO> create(@Valid @RequestBody CreateIncidentRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.createIncident(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Listar incidencias")
    public ResponseEntity<Page<IncidentListResponseDTO>> list(@RequestParam(required = false) IncidentStatus status,
                                                              @RequestParam(required = false) IncidentSeverity severity,
                                                              @RequestParam(required = false) Integer incidentTypeId,
                                                              @RequestParam(required = false) Integer createdById,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                                              Pageable pageable) {
        return ResponseEntity.ok(incidentService.listIncidents(status, severity, incidentTypeId, createdById, from, to, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Detalle de incidencia")
    public ResponseEntity<IncidentResponseDTO> detail(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getById(id));
    }

    @PatchMapping("/{id}/open")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Abrir incidencia")
    public ResponseEntity<IncidentResponseDTO> open(@PathVariable Long id,
                                                    @Valid @RequestBody OpenIncidentRequestDTO request) {
        return ResponseEntity.ok(incidentService.openIncident(id, request));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cerrar incidencia")
    public ResponseEntity<IncidentResponseDTO> close(@PathVariable Long id,
                                                     @Valid @RequestBody CloseIncidentRequestDTO request) {
        return ResponseEntity.ok(incidentService.closeIncident(id, request));
    }

    @PostMapping("/{id}/audits")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Adjuntar auditorías")
    public ResponseEntity<List<IncidentAuditAttachmentResponseDTO>> attachAudits(@PathVariable Long id,
                                                                                  @Valid @RequestBody AttachAuditRequestDTO request) {
        return ResponseEntity.ok(incidentService.attachAudits(id, request));
    }

    @PostMapping("/{id}/audits/{attachmentId}/revert")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revertir auditoría adjunta")
    public ResponseEntity<IncidentAuditAttachmentResponseDTO> revertAudit(@PathVariable Long id,
                                                                          @PathVariable Long attachmentId,
                                                                          @Valid @RequestBody RevertAuditFromIncidentRequestDTO request) {
        request.setAuditAttachmentId(attachmentId);
        return ResponseEntity.ok(incidentService.revertAuditFromIncident(id, attachmentId, request));
    }

    @GetMapping("/{id}/attachable-audits")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Listar auditorías adjuntables")
    public ResponseEntity<List<RecipeCookingAuditResponseDTO>> attachableAudits(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getAttachableAudits(id));
    }

    @GetMapping("/{id}/chat")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Obtener historial de chat")
    public ResponseEntity<Page<IncidentChatMessageResponseDTO>> chat(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(incidentChatService.getHistory(id, pageable));
    }

    @PostMapping("/{id}/chat/mark-read")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Marcar mensajes del chat como leídos")
    public ResponseEntity<Void> markChatAsRead(@PathVariable Long id) {
        incidentChatService.markMessagesAsRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Enviar mensaje al chat")
    public ResponseEntity<IncidentChatMessageResponseDTO> sendChatMessage(@PathVariable Long id,
                                                                          @RequestParam(required = false) String content,
                                                                          @RequestPart(required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentChatService.sendMessage(id, content, file));
    }

    @GetMapping("/{id}/chat/attachments/{messageId}")
    @PreAuthorize("hasAnyRole('ADMIN','CHEF','ELEVATED')")
    @Operation(summary = "Descargar adjunto de chat")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id,
                                                       @PathVariable Long messageId) {
        IncidentChatMessageResponseDTO message = incidentChatService.getMessage(id, messageId);
        Resource resource = incidentChatService.downloadAttachment(id, messageId);

        String filename = message.getAttachmentFilename() != null ? message.getAttachmentFilename() : "attachment";
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (message.getAttachmentContentType() != null && !message.getAttachmentContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(message.getAttachmentContentType());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar incidencia a PDF")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        IncidentResponseDTO incident = incidentService.getById(id);
        List<IncidentChatMessageResponseDTO> chat = incidentChatService.getHistory(id, Pageable.unpaged()).getContent();
        byte[] pdf = incidentReportPdfService.generateIncidentReport(incident, chat);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incident-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
