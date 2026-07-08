package com.economato.inventory.application.usecase.incident;
import com.economato.inventory.application.usecase.crisis.TraceabilityService;
import com.economato.inventory.application.dto.crisis.response.ReverseTraceabilityDTO;

import com.economato.inventory.application.dto.incident.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.incident.response.IncidentTypeResponseDTO;
import com.economato.inventory.application.dto.user.response.UserSummaryDTO;
import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.infrastructure.adapter.out.external.incident.reports.IncidentReportPdfService;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentReportPdfServiceTest {

    @Mock
    private I18nService i18nService;

    @Mock
    private TraceabilityService traceabilityService;

    private IncidentReportPdfService service;

    @BeforeEach
    void setUp() {
        service = new IncidentReportPdfService(i18nService, traceabilityService);

        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).name());
        lenient().when(i18nService.getMessage(any(MessageKey.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenAnswer(invocation -> {
                    MessageKey key = invocation.getArgument(0, MessageKey.class);
                Object[] args = Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length);
                return key.name() + " " + Arrays.toString(args);
                });
        lenient().when(i18nService.getMessage(eq(MessageKey.TRACEABILITY_SUMMARY_REVERSE), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenAnswer(invocation -> "traceability-summary");

        var reverseTraceability = mock(ReverseTraceabilityDTO.class);
        lenient().when(reverseTraceability.getIngredientTrace()).thenReturn(List.of());
        lenient().when(traceabilityService.getReverseTraceability(any(Long.class))).thenReturn(reverseTraceability);
    }

    @Test
    void generatePdf_WithCompleteIncident_ShouldGenerateValidPdf() {
        IncidentResponseDTO incident = completeIncident();
        List<IncidentChatMessageResponseDTO> chat = List.of(
                IncidentChatMessageResponseDTO.builder()
                        .id(1L)
                        .authorName("Admin")
                        .content("Investigating")
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        byte[] pdf = service.generateIncidentReport(incident, chat);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4)));
    }

    @Test
    void generatePdf_WithNoAuditsNorChat_ShouldGenerateValidPdf() {
        IncidentResponseDTO incident = completeIncident();
        incident.setAttachedAudits(List.of());

        byte[] pdf = service.generateIncidentReport(incident, List.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4)));
    }

    @Test
    void generatePdf_WithRevertedAudit_ShouldHandleGracefully() {
        IncidentResponseDTO incident = completeIncident();
        incident.setAttachedAudits(List.of(
                IncidentAuditAttachmentResponseDTO.builder()
                        .id(9L)
                        .cookingAuditId(999L)
                        .recipeName(null)
                        .cookingDate(null)
                        .quantityCooked(null)
                        .reverted(true)
                        .build()
        ));

        byte[] pdf = service.generateIncidentReport(incident, List.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4)));
    }

    @Test
    void generatePdf_WhenIncidentInStatusCreado_ShouldGenerateValidPdf() {
        IncidentResponseDTO incident = completeIncident();
        incident.setStatus(IncidentStatus.CREADO);
        incident.setSeverity(null);
        incident.setOpenedAt(null);
        incident.setClosedAt(null);
        incident.setResolution(null);

        byte[] pdf = service.generateIncidentReport(incident, List.of());

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4)));
    }

    private IncidentResponseDTO completeIncident() {
        return IncidentResponseDTO.builder()
                .id(100L)
                .incidentType(IncidentTypeResponseDTO.builder().id(1).name("Type A").isActive(true).build())
                .title("Contamination")
                .description("Description")
                .status(IncidentStatus.CERRADO_CON_RESOLUCION)
                .severity(IncidentSeverity.ALTA)
                .createdBy(new UserSummaryDTO(1, "Chef", "chef", Role.CHEF))
                .relatedTeacher(new UserSummaryDTO(2, "Teacher", "teacher", Role.CHEF))
                .resolution("Resolved")
                .openedAt(LocalDateTime.now().minusHours(2))
                .closedAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusHours(3))
                .attachedAudits(List.of(
                        IncidentAuditAttachmentResponseDTO.builder()
                                .id(1L)
                                .cookingAuditId(200L)
                                .recipeName("Rice")
                                .cookingDate(LocalDateTime.now().minusDays(1))
                                .quantityCooked(new BigDecimal("2.5"))
                                .reverted(false)
                                .build()
                ))
                .build();
    }
}