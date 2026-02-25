package com.economato.inventory.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

@Service
public class OrderPdfService {

        private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
        private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
        private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
        private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
        private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
        private static final Color SECTION_TITLE_COLOR = new DeviceRgb(55, 65, 81);
        private static final Color ROW_HOVER_BG = new DeviceRgb(243, 244, 246);

        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        public byte[] generateOrderPdf(OrderResponseDTO order) throws Exception {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdfDoc = new PdfDocument(writer);
                try (Document document = new Document(pdfDoc, PageSize.A4)) {
                        document.setMargins(40, 40, 40, 40);

                        PdfFont regularFont = PdfFontFactory.createFont("Helvetica");
                        PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

                        addHeader(document, order, boldFont);
                        addOrderInfoSection(document, order, boldFont, regularFont);
                        addProductsTable(document, order.getDetails(), boldFont, regularFont);
                        addTotalBanner(document, order.getTotalPrice(), boldFont);
                        addFooter(document, regularFont);
                }
                return baos.toByteArray();
        }

        // ===== HEADER =====
        // Mimics .modal-header: full-width gradient banner with centered title

        private void addHeader(Document document, OrderResponseDTO order, PdfFont boldFont) {
                // Main title banner
                Table headerTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setBackgroundColor(PRIMARY_COLOR)
                                .setMarginBottom(0);

                Cell titleCell = new Cell()
                                .add(new Paragraph(sanitizePdfText("Detalles del Pedido #" + order.getId()))
                                                .setFont(boldFont)
                                                .setFontSize(20)
                                                .setFontColor(ColorConstants.WHITE))
                                .setBorder(Border.NO_BORDER)
                                .setPadding(20)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE);

                Cell statusCell = new Cell()
                                .add(new Paragraph(sanitizePdfText(translateStatusToEs(order.getStatus())))
                                                .setFont(boldFont)
                                                .setFontSize(9)
                                                .setFontColor(ColorConstants.WHITE)
                                                .setTextAlignment(TextAlignment.CENTER))
                                .setBorder(Border.NO_BORDER)
                                .setPadding(20)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE);

                headerTable.addCell(titleCell);
                headerTable.addCell(statusCell);
                document.add(headerTable);

                // Thin accent line below header
                Paragraph accent = new Paragraph("")
                                .setBackgroundColor(SECONDARY_COLOR)
                                .setPaddingTop(3)
                                .setMarginTop(0)
                                .setMarginBottom(24);
                document.add(accent);
        }

        // ===== ORDER INFO =====
        // Mimics .info-section with .info-grid: clean label/value pairs, no heavy
        // borders

        private void addOrderInfoSection(Document document, OrderResponseDTO order, PdfFont boldFont,
                        PdfFont regularFont) {
                addSectionTitle(document, "Informacion del Pedido", boldFont);

                Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 2, 1, 2 }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setMarginBottom(24);

                addInfoPair(infoTable, "USUARIO:", sanitizePdfText(order.getUserName()), boldFont, regularFont);
                addInfoPair(infoTable, "ESTADO:", translateStatusToEs(order.getStatus()), boldFont, regularFont);
                addInfoPair(infoTable, "FECHA:",
                                order.getOrderDate() == null ? "" : order.getOrderDate().format(DATE_FORMAT),
                                boldFont, regularFont);
                addInfoPair(infoTable, "PRODUCTOS:",
                                String.valueOf(order.getDetails() == null ? 0 : order.getDetails().size()),
                                boldFont, regularFont);

                document.add(infoTable);
        }

        private void addSectionTitle(Document document, String title, PdfFont boldFont) {
                Paragraph sectionTitle = new Paragraph(title)
                                .setFont(boldFont)
                                .setFontSize(14)
                                .setFontColor(SECTION_TITLE_COLOR)
                                .setMarginBottom(4)
                                .setPaddingBottom(8)
                                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2));
                document.add(sectionTitle);
        }

        private void addInfoPair(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
                Cell labelCell = new Cell()
                                .add(new Paragraph(label)
                                                .setFont(boldFont)
                                                .setFontSize(8)
                                                .setFontColor(TEXT_GRAY)
                                                .setCharacterSpacing(0.5f))
                                .setBorder(Border.NO_BORDER)
                                .setPaddingTop(8)
                                .setPaddingBottom(4);

                Cell valueCell = new Cell()
                                .add(new Paragraph(sanitizePdfText(value))
                                                .setFont(regularFont)
                                                .setFontSize(10)
                                                .setFontColor(TEXT_DARK))
                                .setBorder(Border.NO_BORDER)
                                .setPaddingTop(8)
                                .setPaddingBottom(4);

                table.addCell(labelCell);
                table.addCell(valueCell);
        }

        // ===== PRODUCTS TABLE =====
        // Mimics .products-table: colored header row, clean rows with subtle dividers

        private void addProductsTable(Document document, List<OrderDetailResponseDTO> details,
                        PdfFont boldFont, PdfFont regularFont) {
                addSectionTitle(document, "Productos (" + (details == null ? 0 : details.size()) + ")", boldFont);

                Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setMarginTop(8)
                                .setMarginBottom(8);

                // Header row - matches .table-header (primary color bg, white text)
                table.addHeaderCell(createTableHeaderCell("Producto", boldFont));
                table.addHeaderCell(createTableHeaderCell("Cantidad", boldFont));
                table.addHeaderCell(createTableHeaderCell("Precio Unit.", boldFont));
                table.addHeaderCell(createTableHeaderCell("Subtotal", boldFont));

                // Data rows - subtle bottom border only, alternating bg for readability
                if (details != null) {
                        for (int i = 0; i < details.size(); i++) {
                                OrderDetailResponseDTO detail = details.get(i);
                                Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

                                table.addCell(createTableDataCell(sanitizePdfText(detail.getProductName()), boldFont,
                                                rowBg, true));
                                table.addCell(
                                                createTableDataCell(formatDecimal(detail.getQuantity()) + " uds",
                                                                regularFont, rowBg, false));
                                table.addCell(createTableDataCell(formatCurrency(detail.getUnitPrice()), regularFont,
                                                rowBg, false));
                                table.addCell(createTableDataCellAccent(formatCurrency(detail.getSubtotal()), boldFont,
                                                rowBg));
                        }
                }

                document.add(table);
        }

        private Cell createTableHeaderCell(String text, PdfFont boldFont) {
                return new Cell()
                                .add(new Paragraph(text)
                                                .setFont(boldFont)
                                                .setFontSize(9)
                                                .setFontColor(ColorConstants.WHITE)
                                                .setCharacterSpacing(0.5f))
                                .setBackgroundColor(PRIMARY_COLOR)
                                .setBorder(Border.NO_BORDER)
                                .setPadding(12)
                                .setTextAlignment(TextAlignment.LEFT);
        }

        private Cell createTableDataCell(String text, PdfFont font, Color bgColor, boolean isBold) {
                Paragraph p = new Paragraph(text)
                                .setFont(font)
                                .setFontSize(9)
                                .setFontColor(isBold ? TEXT_DARK : TEXT_GRAY);

                return new Cell()
                                .add(p)
                                .setBackgroundColor(bgColor)
                                .setBorder(Border.NO_BORDER)
                                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
                                .setPadding(10)
                                .setTextAlignment(TextAlignment.LEFT);
        }

        private Cell createTableDataCellAccent(String text, PdfFont boldFont, Color bgColor) {
                return new Cell()
                                .add(new Paragraph(text)
                                                .setFont(boldFont)
                                                .setFontSize(9)
                                                .setFontColor(PRIMARY_COLOR))
                                .setBackgroundColor(bgColor)
                                .setBorder(Border.NO_BORDER)
                                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
                                .setPadding(10)
                                .setTextAlignment(TextAlignment.LEFT);
        }

        // ===== TOTAL BANNER =====
        // Mimics .order-total: full-width gradient banner with large total

        private void addTotalBanner(Document document, BigDecimal totalPrice, PdfFont boldFont) {
                Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setBackgroundColor(PRIMARY_COLOR)
                                .setMarginTop(12)
                                .setMarginBottom(20);

                Cell labelCell = new Cell()
                                .add(new Paragraph("Total del Pedido:")
                                                .setFont(boldFont)
                                                .setFontSize(14)
                                                .setFontColor(ColorConstants.WHITE))
                                .setBorder(Border.NO_BORDER)
                                .setPadding(16)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE);

                Cell valueCell = new Cell()
                                .add(new Paragraph(formatCurrency(totalPrice))
                                                .setFont(boldFont)
                                                .setFontSize(22)
                                                .setFontColor(ColorConstants.WHITE)
                                                .setTextAlignment(TextAlignment.RIGHT))
                                .setBorder(Border.NO_BORDER)
                                .setPadding(16)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE);

                totalTable.addCell(labelCell);
                totalTable.addCell(valueCell);
                document.add(totalTable);
        }

        // ===== FOOTER =====

        private void addFooter(Document document, PdfFont regularFont) {
                Paragraph footer = new Paragraph("Generado por Smart Economato")
                                .setFont(regularFont)
                                .setFontSize(8)
                                .setFontColor(TEXT_GRAY)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginTop(20);
                document.add(footer);
        }

        // ===== HELPERS =====

        private String formatCurrency(BigDecimal value) {
                if (value == null) {
                        return "0.00 \u20ac";
                }
                return String.format("%.2f \u20ac", value);
        }

        private String formatDecimal(BigDecimal value) {
                if (value == null) {
                        return "0";
                }
                return String.format("%.2f", value);
        }

        private String sanitizePdfText(String value) {
                if (value == null) {
                        return "";
                }
                return value.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\u00FF]", "");
        }

        private String translateStatusToEs(String status) {
                if (status == null) {
                        return "";
                }
                return switch (status.trim().toUpperCase()) {
                        case "CREATED" -> "CREADO";
                        case "PENDING" -> "PENDIENTE";
                        case "REVIEW" -> "EN REVISION";
                        case "CONFIRMED" -> "CONFIRMADO";
                        case "INCOMPLETE" -> "INCOMPLETO";
                        case "CANCELLED" -> "CANCELADO";
                        default -> status;
                };
        }
}
