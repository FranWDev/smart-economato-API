package com.economato.inventory.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
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
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

@Service
public class RecipePdfService {

    private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
    private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
    private static final Color LIGHT_BG = new DeviceRgb(249, 250, 251);
    private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
    private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
    private static final Color SECTION_TITLE_COLOR = new DeviceRgb(55, 65, 81);
    private static final Color ROW_HOVER_BG = new DeviceRgb(243, 244, 246);
    private static final Color ALLERGEN_BG = new DeviceRgb(254, 242, 242);
    private static final Color ALLERGEN_TEXT = new DeviceRgb(220, 38, 38);
    private static final Color GREEN_TEXT = new DeviceRgb(16, 185, 129);

    public byte[] generateRecipePdf(RecipeResponseDTO recipe) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);

        document.setMargins(40, 40, 40, 40);

        PdfFont regularFont = PdfFontFactory.createFont("Helvetica");
        PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

        addHeader(document, sanitizePdfText(recipe.getName()), boldFont);

        if (recipe.getPresentation() != null && !recipe.getPresentation().isEmpty()) {
            addSection(document, "Presentacion", sanitizePdfText(recipe.getPresentation()), boldFont, regularFont);
        }

        if (recipe.getElaboration() != null && !recipe.getElaboration().isEmpty()) {
            addElaborationSection(document, sanitizePdfText(recipe.getElaboration()), boldFont, regularFont);
        }

        if (recipe.getComponents() != null && !recipe.getComponents().isEmpty()) {
            addIngredientsTable(document, recipe.getComponents(), boldFont, regularFont);
        }

        addCostBanner(document, recipe.getTotalCost(), boldFont);

        addAllergensSection(document, recipe.getAllergens(), boldFont, regularFont);

        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    // ===== HEADER =====
    // Mimics .modal-header: full-width gradient banner with centered title

    private void addHeader(Document document, String recipeName, PdfFont boldFont) {
        Paragraph header = new Paragraph(recipeName)
                .setFont(boldFont)
                .setFontSize(22)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(24)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(0);
        document.add(header);

        // Thin accent line below header
        Paragraph accent = new Paragraph("")
                .setBackgroundColor(SECONDARY_COLOR)
                .setPaddingTop(3)
                .setMarginTop(0)
                .setMarginBottom(24);
        document.add(accent);
    }

    // ===== SECTION TITLE =====
    // Mimics .modal-section h4: bold title with primary-colored bottom border

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

    // ===== TEXT SECTION =====

    private void addSection(Document document, String title, String content, PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, title, boldFont);

        Paragraph sectionContent = new Paragraph(content)
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(TEXT_GRAY)
                .setFixedLeading(18f)
                .setMarginTop(8)
                .setMarginBottom(20);
        document.add(sectionContent);
    }

    // ===== ELABORATION =====
    // Mimics .elaboration-steps: numbered list with clean spacing

    private void addElaborationSection(Document document, String elaboration, PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, "Elaboracion", boldFont);

        List<String> steps = parseElaborationSteps(elaboration);

        if (steps.size() > 1) {
            com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List()
                    .setSymbolIndent(12)
                    .setMarginTop(8)
                    .setMarginBottom(20);

            for (String step : steps) {
                ListItem item = new ListItem();
                Paragraph p = new Paragraph(step)
                        .setFont(regularFont)
                        .setFontSize(10)
                        .setFontColor(TEXT_GRAY)
                        .setFixedLeading(18f);
                item.add(p);
                list.add(item);
            }
            document.add(list);
        } else {
            Paragraph content = new Paragraph(elaboration)
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_GRAY)
                    .setFixedLeading(18f)
                    .setMarginTop(8)
                    .setMarginBottom(20);
            document.add(content);
        }
    }

    // ===== INGREDIENTS TABLE =====
    // Mimics .ingredients-table: clean header, subtle row dividers

    private void addIngredientsTable(Document document, List<RecipeComponentResponseDTO> components,
            PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, "Ingredientes", boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8)
                .setMarginBottom(8);

        // Header row - primary color bg, white text
        table.addHeaderCell(createTableHeaderCell("Producto", boldFont));
        table.addHeaderCell(createTableHeaderCell("Cantidad", boldFont));
        table.addHeaderCell(createTableHeaderCell("Subtotal", boldFont));

        for (int i = 0; i < components.size(); i++) {
            RecipeComponentResponseDTO comp = components.get(i);
            Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

            table.addCell(createTableDataCell(sanitizePdfText(comp.getProductName()), boldFont, rowBg, true));
            table.addCell(createTableDataCell(String.format("%.2f", comp.getQuantity()), regularFont, rowBg, false));
            table.addCell(createTableDataCellAccent(
                    String.format("%.2f \u20ac", comp.getSubtotal()), boldFont, rowBg));
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

    // ===== COST BANNER =====
    // Mimics .ingredients-table tfoot: accent row with total cost

    private void addCostBanner(Document document, BigDecimal totalCost, PdfFont boldFont) {
        Table costTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(ALLERGEN_BG)
                .setMarginTop(4)
                .setMarginBottom(24);

        Cell labelCell = new Cell()
                .add(new Paragraph("Coste Total")
                        .setFont(boldFont)
                        .setFontSize(14)
                        .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setPadding(14)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        Cell valueCell = new Cell()
                .add(new Paragraph(String.format("%.2f \u20ac", totalCost))
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(PRIMARY_COLOR)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setPadding(14)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        costTable.addCell(labelCell);
        costTable.addCell(valueCell);
        document.add(costTable);
    }

    // ===== ALLERGENS =====
    // Mimics .allergens-list with .allergen-tag badges

    private void addAllergensSection(Document document, List<AllergenResponseDTO> allergens,
            PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, "Alergenos", boldFont);

        if (allergens == null || allergens.isEmpty()) {
            Paragraph noAllergens = new Paragraph("Esta receta no contiene alergenos conocidos.")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(GREEN_TEXT)
                    .setItalic()
                    .setMarginTop(8)
                    .setMarginBottom(20);
            document.add(noAllergens);
        } else {
            Paragraph allergensLine = new Paragraph()
                    .setMarginTop(8)
                    .setMarginBottom(20);

            for (AllergenResponseDTO allergen : allergens) {
                Text allergenTag = new Text("  " + sanitizePdfText(allergen.getName()) + "  ")
                        .setFont(boldFont)
                        .setFontSize(9)
                        .setFontColor(ALLERGEN_TEXT)
                        .setBackgroundColor(ALLERGEN_BG);

                allergensLine.add(allergenTag);
                allergensLine.add(new Text("  "));
            }
            document.add(allergensLine);
        }
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

    private List<String> parseElaborationSteps(String elaboration) {
        List<String> lines = Arrays.asList(elaboration.split("\\n"));
        List<String> steps = lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.replaceFirst("^\\d+\\.?\\s*", ""))
                .collect(Collectors.toList());
        return steps.isEmpty() ? List.of(elaboration) : steps;
    }

    private String sanitizePdfText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\u00FF]", "");
    }
}
