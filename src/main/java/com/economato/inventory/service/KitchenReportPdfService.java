package com.economato.inventory.service;

import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.dto.response.ProductStatDTO;
import com.economato.inventory.dto.response.RecipeStatDTO;
import com.economato.inventory.dto.response.UserStatDTO;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class KitchenReportPdfService {

    private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
    private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
    private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
    private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
    private static final Color SECTION_TITLE_COLOR = new DeviceRgb(55, 65, 81);
    private static final Color ROW_HOVER_BG = new DeviceRgb(243, 244, 246);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generateKitchenReportPdf(KitchenReportResponseDTO report) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            try (Document document = new Document(pdfDoc, PageSize.A4)) {
                document.setMargins(40, 40, 40, 40);

                PdfFont regularFont = PdfFontFactory.createFont("Helvetica");
                PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

                addHeader(document, report, boldFont);
                addReportInfoSection(document, report, boldFont, regularFont);

                addTopRecipesTable(document, report.getTopRecipes(), boldFont, regularFont);
                document.add(new Paragraph("\n"));

                addTopUsersTable(document, report.getTopUsers(), boldFont, regularFont);
                document.add(new Paragraph("\n"));

                addTopProductsTable(document, report.getTopProducts(), boldFont, regularFont);

                addTotalBanner(document, report.getTotalEstimatedCost(), boldFont);
                addFooter(document, regularFont);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF del reporte de cocina", e);
        }
    }

    private void addHeader(Document document, KitchenReportResponseDTO report, PdfFont boldFont) {
        Table headerTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(PRIMARY_COLOR)
                .setMarginBottom(0);

        Cell titleCell = new Cell()
                .add(new Paragraph(sanitizePdfText("Reporte de Cocina"))
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(ColorConstants.WHITE))
                .setBorder(Border.NO_BORDER)
                .setPadding(20)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        Cell statusCell = new Cell()
                .add(new Paragraph(sanitizePdfText("Periodo: " + report.getReportPeriod()))
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

        Paragraph accent = new Paragraph("")
                .setBackgroundColor(SECONDARY_COLOR)
                .setPaddingTop(3)
                .setMarginTop(0)
                .setMarginBottom(24);
        document.add(accent);
    }

    private void addReportInfoSection(Document document, KitchenReportResponseDTO report, PdfFont boldFont,
            PdfFont regularFont) {
        addSectionTitle(document, "Información General", boldFont);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 2, 1, 2 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(24);

        addInfoPair(infoTable, "SESIONES:", String.valueOf(report.getTotalCookingSessions()), boldFont, regularFont);
        addInfoPair(infoTable, "PORCIONES TOTALES:", formatDecimal(report.getTotalPortionsCooked()), boldFont,
                regularFont);
        addInfoPair(infoTable, "RECETAS DISTINTAS:", String.valueOf(report.getDistinctRecipesCooked()), boldFont,
                regularFont);
        addInfoPair(infoTable, "PRODUCTOS DISTINTOS:", String.valueOf(report.getDistinctProductsUsed()), boldFont,
                regularFont);
        addInfoPair(infoTable, "USUARIOS ÚNICOS:", String.valueOf(report.getDistinctUsersCooking()), boldFont,
                regularFont);
        addInfoPair(infoTable, "FECHA EMISIÓN:", LocalDateTime.now().format(DATE_FORMAT), boldFont, regularFont);

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

    // TOP RECIPES
    private void addTopRecipesTable(Document document, List<RecipeStatDTO> details, PdfFont boldFont,
            PdfFont regularFont) {
        addSectionTitle(document, "Top Recetas Cocinadas", boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8)
                .setMarginBottom(8);

        table.addHeaderCell(createTableHeaderCell("Receta", boldFont));
        table.addHeaderCell(createTableHeaderCell("Veces Cocinada", boldFont));
        table.addHeaderCell(createTableHeaderCell("Raciones Totales", boldFont));

        if (details != null && !details.isEmpty()) {
            for (int i = 0; i < details.size(); i++) {
                RecipeStatDTO detail = details.get(i);
                Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

                table.addCell(createTableDataCell(sanitizePdfText(detail.getRecipeName()), boldFont, rowBg, true));
                table.addCell(createTableDataCell(String.valueOf(detail.getTimesCooked()), regularFont, rowBg, false));
                table.addCell(
                        createTableDataCell(formatDecimal(detail.getTotalQuantityCooked()), regularFont, rowBg, false));
            }
        } else {
            table.addCell(createTableDataCell("Sin datos", regularFont, ColorConstants.WHITE, false));
            table.addCell(createTableDataCell("-", regularFont, ColorConstants.WHITE, false));
            table.addCell(createTableDataCell("-", regularFont, ColorConstants.WHITE, false));
        }
        document.add(table);
    }

    // TOP USERS
    private void addTopUsersTable(Document document, List<UserStatDTO> details, PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, "Usuarios que más han cocinado", boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8)
                .setMarginBottom(8);

        table.addHeaderCell(createTableHeaderCell("Usuario", boldFont));
        table.addHeaderCell(createTableHeaderCell("Veces Cocinadas", boldFont));

        if (details != null && !details.isEmpty()) {
            for (int i = 0; i < details.size(); i++) {
                UserStatDTO detail = details.get(i);
                Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

                table.addCell(createTableDataCell(sanitizePdfText(detail.getUserName()), boldFont, rowBg, true));
                table.addCell(createTableDataCell(String.valueOf(detail.getTimesCooked()), regularFont, rowBg, false));
            }
        } else {
            table.addCell(createTableDataCell("Sin datos", regularFont, ColorConstants.WHITE, false));
            table.addCell(createTableDataCell("-", regularFont, ColorConstants.WHITE, false));
        }
        document.add(table);
    }

    // TOP PRODUCTS
    private void addTopProductsTable(Document document, List<ProductStatDTO> details, PdfFont boldFont,
            PdfFont regularFont) {
        addSectionTitle(document, "Consumo de Productos", boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8)
                .setMarginBottom(8);

        table.addHeaderCell(createTableHeaderCell("Producto", boldFont));
        table.addHeaderCell(createTableHeaderCell("Cantidad Consumida", boldFont));
        table.addHeaderCell(createTableHeaderCell("Coste Estimado", boldFont));

        if (details != null && !details.isEmpty()) {
            for (int i = 0; i < details.size(); i++) {
                ProductStatDTO detail = details.get(i);
                Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

                table.addCell(createTableDataCell(sanitizePdfText(detail.getProductName()), boldFont, rowBg, true));
                table.addCell(
                        createTableDataCell(formatDecimal(detail.getTotalQuantityUsed()), regularFont, rowBg, false));
                table.addCell(createTableDataCellAccent(formatCurrency(detail.getEstimatedCost()), boldFont, rowBg));
            }
        } else {
            table.addCell(createTableDataCell("Sin datos", regularFont, ColorConstants.WHITE, false));
            table.addCell(createTableDataCell("-", regularFont, ColorConstants.WHITE, false));
            table.addCell(createTableDataCell("-", regularFont, ColorConstants.WHITE, false));
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

    private void addTotalBanner(Document document, BigDecimal totalPrice, PdfFont boldFont) {
        Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(PRIMARY_COLOR)
                .setMarginTop(12)
                .setMarginBottom(20);

        Cell labelCell = new Cell()
                .add(new Paragraph("Costo Estimado Producción:")
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

    private void addFooter(Document document, PdfFont regularFont) {
        Paragraph footer = new Paragraph("Generado por Smart Economato")
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20);
        document.add(footer);
    }

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
}
