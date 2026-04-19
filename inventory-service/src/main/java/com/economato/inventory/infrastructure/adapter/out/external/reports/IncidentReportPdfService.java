package com.economato.inventory.infrastructure.adapter.out.external.reports;

import com.economato.inventory.application.dto.response.IncidentAuditAttachmentResponseDTO;
import com.economato.inventory.application.dto.response.IncidentChatMessageResponseDTO;
import com.economato.inventory.application.dto.response.IncidentResponseDTO;
import com.economato.inventory.application.usecase.TraceabilityService;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentReportPdfService {

    private final I18nService i18nService;
    private final TraceabilityService traceabilityService;

    private static final DeviceRgb HEADER = new DeviceRgb(31, 77, 114);

    public byte[] generateIncidentReport(IncidentResponseDTO incident,
                                         List<IncidentChatMessageResponseDTO> chatMessages) {
        if (incident == null) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_REPORT_INCIDENT_INVALID_INPUT));
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            addHeader(document, incident, bold);
            addGeneralInfo(document, incident, bold, regular);
            addAuditSection(document, incident.getAttachedAudits(), bold, regular);
            addChatSection(document, chatMessages, bold, regular);

            document.add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_GENERATED) + ": "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .setFont(regular).setFontSize(8));

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("Error generating incident PDF report", ex);
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_REPORT_INCIDENT_PDF_GENERATION), ex);
        }
    }

    private void addHeader(Document document, IncidentResponseDTO incident, PdfFont bold) {
        String title = i18nService.getMessage(MessageKey.INCIDENT_REPORT_TITLE);
        Paragraph p = new Paragraph(title + " #" + incident.getId())
                .setFont(bold)
                .setFontSize(16)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(HEADER)
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(p);
    }

    private void addGeneralInfo(Document document, IncidentResponseDTO incident, PdfFont bold, PdfFont regular) {
        document.add(new Paragraph(i18nService.getMessage(MessageKey.INCIDENT_REPORT_SECTION_GENERAL_INFO))
                .setFont(bold).setFontSize(12).setMarginTop(12));

        Table info = new Table(new float[]{1, 2}).setWidth(UnitValue.createPercentValue(100));
        addRow(info, i18nService.getMessage(MessageKey.REPORT_COLUMN_ID), String.valueOf(incident.getId()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.REPORT_LABEL_STATUS), String.valueOf(incident.getStatus()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_SEVERITY), String.valueOf(incident.getSeverity()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_TYPE), incident.getIncidentType() != null ? incident.getIncidentType().getName() : "-", bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_CREATOR), incident.getCreatedBy() != null ? incident.getCreatedBy().getName() : "-", bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_RELATED_TEACHER), incident.getRelatedTeacher() != null ? incident.getRelatedTeacher().getName() : "-", bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_CREATED_AT), formatDate(incident.getCreatedAt()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_OPENED_AT), formatDate(incident.getOpenedAt()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_CLOSED_AT), formatDate(incident.getClosedAt()), bold, regular);
        addRow(info, i18nService.getMessage(MessageKey.INCIDENT_REPORT_LABEL_RESOLUTION), incident.getResolution() != null ? incident.getResolution() : "-", bold, regular);
        document.add(info);
    }

    private void addAuditSection(Document document,
                                 List<IncidentAuditAttachmentResponseDTO> attachments,
                                 PdfFont bold,
                                 PdfFont regular) {
        document.add(new Paragraph(i18nService.getMessage(MessageKey.INCIDENT_REPORT_SECTION_AUDITS))
                .setFont(bold).setFontSize(12).setMarginTop(12));

        Table table = new Table(new float[]{1, 2, 2, 1, 1})
                .setWidth(UnitValue.createPercentValue(100));
        header(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_ID), bold);
        header(table, i18nService.getMessage(MessageKey.INCIDENT_REPORT_COLUMN_RECIPE), bold);
        header(table, i18nService.getMessage(MessageKey.REPORT_LABEL_DATE), bold);
        header(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_QUANTITY), bold);
        header(table, i18nService.getMessage(MessageKey.REPORT_LABEL_STATUS), bold);

        if (attachments != null && !attachments.isEmpty()) {
            for (IncidentAuditAttachmentResponseDTO attachment : attachments) {
                data(table, String.valueOf(attachment.getCookingAuditId()), regular);
                data(table, valueOrDash(attachment.getRecipeName()), regular);
                data(table, formatDate(attachment.getCookingDate()), regular);
                data(table, attachment.getQuantityCooked() != null ? attachment.getQuantityCooked().toPlainString() : "-", regular);
                data(table, attachment.isReverted()
                    ? i18nService.getMessage(MessageKey.INCIDENT_REPORT_STATUS_REVERTED)
                    : i18nService.getMessage(MessageKey.INCIDENT_REPORT_STATUS_ACTIVE), regular);
            }
        } else {
            Cell cell = new Cell(1, 5).add(new Paragraph("-").setFont(regular));
            table.addCell(cell);
        }

        document.add(table);

        if (attachments != null) {
            for (IncidentAuditAttachmentResponseDTO attachment : attachments) {
                if (attachment.getCookingAuditId() != null) {
                    try {
                        var reverse = traceabilityService.getReverseTraceability(attachment.getCookingAuditId());
                        addTraceabilityDetail(document, reverse, bold, regular);
                    } catch (Exception ex) {
                        log.warn("Unable to render reverse traceability for cookingAuditId={}", attachment.getCookingAuditId(), ex);
                        document.add(new Paragraph(i18nService.getMessage(
                                MessageKey.INCIDENT_REPORT_TRACEABILITY_UNAVAILABLE,
                                attachment.getCookingAuditId()))
                                .setFont(regular).setFontSize(8).setFontColor(ColorConstants.RED));
                    }
                }
            }
        }
    }

    private void addTraceabilityDetail(Document document, com.economato.inventory.application.dto.response.ReverseTraceabilityDTO reverse, PdfFont bold, PdfFont regular) {
        if (reverse == null || reverse.getIngredientTrace() == null || reverse.getIngredientTrace().isEmpty()) {
            return;
        }

        document.add(new Paragraph(i18nService.getMessage(MessageKey.TRACEABILITY_SUMMARY_REVERSE, reverse.getCookingAudit().getId()))
                .setFont(bold).setFontSize(10).setMarginTop(10).setKeepWithNext(true));

        Table table = new Table(new float[]{3, 2, 2, 1}).setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
        
        table.addCell(new Cell().add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_COLUMN_PRODUCT)).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE)).setBackgroundColor(ColorConstants.GRAY));
        table.addCell(new Cell().add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_COLUMN_BATCH_CODE)).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE)).setBackgroundColor(ColorConstants.GRAY));
        table.addCell(new Cell().add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_COLUMN_SUPPLIER)).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE)).setBackgroundColor(ColorConstants.GRAY));
        table.addCell(new Cell().add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_COLUMN_ID)).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE)).setBackgroundColor(ColorConstants.GRAY));

        for (var trace : reverse.getIngredientTrace()) {
            table.addCell(new Cell().add(new Paragraph(valueOrDash(trace.getProductName())).setFont(regular).setFontSize(8)));
            
            String batchLabel = "-";
            if (trace.getBatchCode() != null && !trace.getBatchCode().isBlank()) {
                batchLabel = trace.getBatchCode();
            } else if (trace.getBatchId() != null) {
                batchLabel = "#" + trace.getBatchId();
            }
            table.addCell(new Cell().add(new Paragraph(batchLabel).setFont(regular).setFontSize(8)));
            
            table.addCell(new Cell().add(new Paragraph(valueOrDash(trace.getSupplierName())).setFont(regular).setFontSize(8)));
            table.addCell(new Cell().add(new Paragraph(trace.getOrderId() != null ? String.valueOf(trace.getOrderId()) : "-").setFont(regular).setFontSize(8)));
        }
        document.add(table);
    }

    private void addChatSection(Document document,
                                List<IncidentChatMessageResponseDTO> messages,
                                PdfFont bold,
                                PdfFont regular) {
        document.add(new Paragraph(i18nService.getMessage(MessageKey.INCIDENT_REPORT_SECTION_CHAT))
                .setFont(bold).setFontSize(12).setMarginTop(12));

        if (messages == null || messages.isEmpty()) {
            document.add(new Paragraph("-").setFont(regular));
            return;
        }

        for (IncidentChatMessageResponseDTO message : messages) {
            String text = "[" + formatDate(message.getCreatedAt()) + "] "
                    + valueOrDash(message.getAuthorName()) + ": "
                    + valueOrDash(message.getContent());
            if (message.isHasAttachment()) {
                text += " " + i18nService.getMessage(
                        MessageKey.INCIDENT_REPORT_CHAT_ATTACHMENT,
                        valueOrDash(message.getAttachmentFilename()));
            }
            document.add(new Paragraph(text).setFont(regular).setFontSize(9));
        }
    }

    private void addRow(Table table, String label, String value, PdfFont bold, PdfFont regular) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(bold).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(valueOrDash(value)).setFont(regular).setFontSize(9)));
    }

    private void header(Table table, String label, PdfFont bold) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(HEADER));
    }

    private void data(Table table, String value, PdfFont regular) {
        table.addCell(new Cell().add(new Paragraph(valueOrDash(value)).setFont(regular).setFontSize(9)));
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
