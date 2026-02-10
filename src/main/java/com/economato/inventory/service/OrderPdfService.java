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
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

@Service
public class OrderPdfService {

    private static final Color HEADER_START = new DeviceRgb(102, 126, 234); // #667eea
    private static final Color HEADER_END = new DeviceRgb(118, 75, 162); // #764ba2
    private static final Color LIGHT_GRAY = new DeviceRgb(249, 250, 251); // #f9fafb
    private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235); // #e5e7eb
    private static final Color TEXT_GRAY = new DeviceRgb(102, 102, 102); // #666

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
            addTotalSection(document, order.getTotalPrice(), boldFont);
            addFooter(document, regularFont);
        }
        return baos.toByteArray();
    }

    private void addHeader(Document document, OrderResponseDTO order, PdfFont boldFont) {
        String title = "Pedido #" + order.getId();
        Paragraph header = new Paragraph(sanitizePdfText(title))
                .setFont(boldFont)
                .setFontSize(20)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(HEADER_START)
                .setPadding(18)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(12);

        Paragraph status = new Paragraph(sanitizePdfText(translateStatusToEs(order.getStatus())))
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(HEADER_END)
                .setPadding(6)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(-8)
                .setMarginBottom(12);

        document.add(header);
        document.add(status);
    }

    private void addOrderInfoSection(Document document, OrderResponseDTO order, PdfFont boldFont, PdfFont regularFont) {
        Paragraph title = new Paragraph("Informacion del Pedido")
                .setFont(boldFont)
                .setFontSize(13)
                .setFontColor(new DeviceRgb(51, 51, 51))
                .setMarginTop(6)
                .setMarginBottom(10);
        document.add(title);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoRow(infoTable, "Usuario", order.getUserName(), boldFont, regularFont);
        addInfoRow(infoTable, "Fecha", order.getOrderDate() == null ? "" : order.getOrderDate().format(DATE_FORMAT), boldFont, regularFont);
        addInfoRow(infoTable, "Estado", translateStatusToEs(order.getStatus()), boldFont, regularFont);

        document.add(infoTable);
    }

    private void addInfoRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(boldFont)
                        .setFontSize(10)
                        .setFontColor(new DeviceRgb(85, 85, 85)))
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(6)
                .setBackgroundColor(LIGHT_GRAY);

        Cell valueCell = new Cell()
                .add(new Paragraph(sanitizePdfText(value))
                        .setFont(regularFont)
                        .setFontSize(10)
                        .setFontColor(new DeviceRgb(51, 51, 51)))
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(6);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addProductsTable(Document document, List<OrderDetailResponseDTO> details,
                                  PdfFont boldFont, PdfFont regularFont) {
        Paragraph title = new Paragraph("Productos")
                .setFont(boldFont)
                .setFontSize(13)
                .setFontColor(new DeviceRgb(51, 51, 51))
                .setMarginBottom(8);
        document.add(title);

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(12);

        table.addHeaderCell(createHeaderCell("Producto", boldFont));
        table.addHeaderCell(createHeaderCell("Cantidad", boldFont));
        table.addHeaderCell(createHeaderCell("Precio Unit.", boldFont));
        table.addHeaderCell(createHeaderCell("Subtotal", boldFont));

        if (details != null) {
            for (OrderDetailResponseDTO detail : details) {
                table.addCell(createDataCell(detail.getProductName(), regularFont));
                table.addCell(createDataCell(formatDecimal(detail.getQuantity()), regularFont));
                table.addCell(createDataCell(formatCurrency(detail.getUnitPrice()), regularFont));
                table.addCell(createDataCell(formatCurrency(detail.getSubtotal()), regularFont));
            }
        }

        document.add(table);
    }

    private void addTotalSection(Document document, BigDecimal totalPrice, PdfFont boldFont) {
        Paragraph total = new Paragraph("Total: " + formatCurrency(totalPrice))
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(HEADER_START)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(6)
                .setMarginBottom(8);
        document.add(total);
    }

    private void addFooter(Document document, PdfFont regularFont) {
        Paragraph footer = new Paragraph("Generado por Smart Economato")
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20);
        document.add(footer);
    }

    private Cell createHeaderCell(String text, PdfFont boldFont) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(boldFont)
                        .setFontSize(10)
                        .setFontColor(new DeviceRgb(55, 65, 81)))
                .setBackgroundColor(LIGHT_GRAY)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setPadding(6)
                .setTextAlignment(TextAlignment.LEFT);
    }

    private Cell createDataCell(String text, PdfFont regularFont) {
        return new Cell()
                .add(new Paragraph(sanitizePdfText(text))
                        .setFont(regularFont)
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(51, 51, 51)))
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.LEFT);
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "0.00 EUR";
        }
        return String.format("%.2f EUR", value);
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
