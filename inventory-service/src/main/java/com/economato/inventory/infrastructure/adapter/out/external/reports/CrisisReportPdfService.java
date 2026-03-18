package com.economato.inventory.infrastructure.adapter.out.external.reports;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
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
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrisisReportPdfService {

    private final I18nService i18nService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RecipeCookingAuditRepository cookingAuditRepository;

    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(220, 38, 38);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb BG_GRAY = new DeviceRgb(243, 244, 246);

    public byte[] generateCrisisReport(CrisisResponseDTO crisisData) {
        log.info("Generating Crisis PDF Report for Crisis ID: {}", crisisData.getCrisisId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            addHeader(document, crisisData, bold);

            addProductsSection(document, crisisData, bold, regular);

            addOrdersSection(document, crisisData.getAffectedOrderIds(), bold, regular);

            addCookingsSection(document, crisisData.getAffectedCookingAuditIds(), bold, regular);

            addIntegritySection(document, crisisData, bold, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating crisis PDF report", e);
            String reportRef = crisisData.getCrisisCode() != null ? crisisData.getCrisisCode() : String.valueOf(crisisData.getCrisisId());
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_REPORT_ORDER_PDF_GENERATION, new Object[]{reportRef}), e);
        }
    }

    private void addHeader(Document document, CrisisResponseDTO data, PdfFont bold) {
        Paragraph title = new Paragraph(i18nService.getMessage(MessageKey.CRISIS_REPORT_TITLE))
                .setFont(bold).setFontSize(18).setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(PRIMARY_COLOR).setPadding(15).setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        Table infoTable = new Table(2).setWidth(UnitValue.createPercentValue(100)).setMarginTop(10).setMarginBottom(20);
        String crisisReference = data.getCrisisCode() != null ? data.getCrisisCode() : String.valueOf(data.getCrisisId());
        addInfoRow(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_CRISIS_ID) + ":", crisisReference, bold, bold);
        addInfoRow(infoTable, i18nService.getMessage(MessageKey.REPORT_COLUMN_SUPPLIER) + ":", data.getSupplierName(), bold, bold);
        addInfoRow(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_REASON) + ":", data.getReason(), bold, bold);
        addInfoRow(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_DATE) + ":", data.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), bold, bold);
        document.add(infoTable);
    }

    private void addProductsSection(Document document, CrisisResponseDTO data, PdfFont bold, PdfFont regular) {
        addSectionTitle(document, i18nService.getMessage(MessageKey.CRISIS_REPORT_SECTION_QUARANTINE_PRODUCTS), bold);
        
        float[] widths = {2, 2, 1.5f, 1.5f, 3};
        Table table = new Table(widths).setWidth(UnitValue.createPercentValue(100)).setMarginBottom(15);
        
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_NAME), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_BARCODE), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_STOCK_CURRENT), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_UNIT), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_LATEST_HASH), bold);

        for (Map.Entry<String, String> entry : data.getQuarantinedProducts().entrySet()) {
            Optional<Product> p = productRepository.findByNameContainingIgnoreCase(entry.getKey()).stream()
                    .filter(product -> !product.isHidden())
                    .findFirst();
            addDataCell(table, entry.getKey(), regular);
            addDataCell(table, p.map(Product::getProductCode).orElse("-"), regular);
            addDataCell(table, p.map(product -> product.getCurrentStock().toString()).orElse("0"), regular);
            addDataCell(table, p.map(Product::getUnit).orElse("-"), regular);
            addDataCell(table, entry.getValue().length() > 12 ? entry.getValue().substring(0, 12) + "..." : entry.getValue(), regular);
        }
        document.add(table);
    }

    private void addOrdersSection(Document document, List<Integer> orderIds, PdfFont bold, PdfFont regular) {
        if (orderIds == null || orderIds.isEmpty()) return;
        addSectionTitle(document, i18nService.getMessage(MessageKey.CRISIS_REPORT_SECTION_AFFECTED_ORDERS), bold);

        Table table = new Table(new float[]{1, 2, 2, 2}).setWidth(UnitValue.createPercentValue(100)).setMarginBottom(15);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_ID), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_LABEL_DATE), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_SUPPLIER), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_LABEL_STATUS), bold);

        List<Order> orders = orderRepository.findAllById(orderIds);
        for (Order o : orders) {
            addDataCell(table, o.getId().toString(), regular);
            addDataCell(table, o.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), regular);
            addDataCell(table, o.getSupplier().getName(), regular);
            addDataCell(table, o.getStatus().name(), regular);
        }
        document.add(table);
    }

    private void addCookingsSection(Document document, List<Long> cookingIds, PdfFont bold, PdfFont regular) {
        if (cookingIds == null || cookingIds.isEmpty()) return;
        addSectionTitle(document, i18nService.getMessage(MessageKey.CRISIS_REPORT_SECTION_AFFECTED_COOKINGS), bold);

        Table table = new Table(new float[]{1, 2, 3, 2}).setWidth(UnitValue.createPercentValue(100)).setMarginBottom(15);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_ID), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_DATETIME), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_RECIPE), bold);
        addHeaderCell(table, i18nService.getMessage(MessageKey.REPORT_COLUMN_QUANTITY), bold);

        List<RecipeCookingAudit> cookings = cookingAuditRepository.findAllById(cookingIds);
        for (RecipeCookingAudit c : cookings) {
            addDataCell(table, c.getId().toString(), regular);
            addDataCell(table, c.getCookingDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), regular);
            addDataCell(table, c.getRecipe().getName(), regular);
            addDataCell(table, c.getQuantityCooked().toString(), regular);
        }
        document.add(table);
    }

    private void addIntegritySection(Document document, CrisisResponseDTO data, PdfFont bold, PdfFont regular) {
        addSectionTitle(document, i18nService.getMessage(MessageKey.CRISIS_REPORT_SECTION_TRACEABILITY_CHAIN), bold);
        
        String statusText = data.isIntegrityVerified() ? i18nService.getMessage(MessageKey.STATUS_OK) : i18nService.getMessage(MessageKey.STATUS_CORRUPT);
        
        Paragraph p = new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_INTEGRITY_STATUS) + " " + statusText)
                .setFont(bold).setFontSize(10).setFontColor(data.isIntegrityVerified() ? ColorConstants.GREEN : ColorConstants.RED);
        document.add(p);

        Paragraph notice = new Paragraph(i18nService.getMessage(MessageKey.REPORT_LEGAL_NOTICE))
                .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(10).setPadding(5).setBackgroundColor(BG_GRAY);
        document.add(notice);
    }

    private void addSectionTitle(Document document, String title, PdfFont bold) {
        document.add(new Paragraph(title).setFont(bold).setFontSize(12).setFontColor(PRIMARY_COLOR).setMarginBottom(5).setBorderBottom(new SolidBorder(PRIMARY_COLOR, 1)));
    }

    private void addHeaderCell(Table table, String text, PdfFont font) {
        table.addCell(new Cell().add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(ColorConstants.WHITE)).setBackgroundColor(PRIMARY_COLOR).setPadding(5));
    }

    private void addDataCell(Table table, String text, PdfFont font) {
        table.addCell(new Cell().add(new Paragraph(text).setFont(font).setFontSize(8)).setPadding(5).setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)));
    }

    private void addInfoRow(Table table, String label, String value, PdfFont boldLabel, PdfFont fontValue) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(boldLabel).setFontSize(9).setFontColor(TEXT_GRAY)).setPadding(5).setBorder(null));
        table.addCell(new Cell().add(new Paragraph(value).setFont(fontValue).setFontSize(9).setFontColor(TEXT_DARK)).setPadding(5).setBorder(null));
    }
}
