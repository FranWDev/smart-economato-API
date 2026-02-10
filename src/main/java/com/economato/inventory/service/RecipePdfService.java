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

@Service
public class RecipePdfService {

    private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
    private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
    private static final Color LIGHT_GRAY = new DeviceRgb(249, 250, 251);
    private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
    private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
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
            addSection(document, "Presentación", sanitizePdfText(recipe.getPresentation()), boldFont, regularFont);
        }

        if (recipe.getComponents() != null && !recipe.getComponents().isEmpty()) {
            addIngredientsTable(document, recipe.getComponents(), boldFont, regularFont);
        }

        addCostSection(document, recipe.getTotalCost(), boldFont, regularFont);

        if (recipe.getElaboration() != null && !recipe.getElaboration().isEmpty()) {
            addElaborationSection(document, sanitizePdfText(recipe.getElaboration()), boldFont, regularFont);
        }

        addAllergensSection(document, recipe.getAllergens(), boldFont, regularFont);

        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    private void addHeader(Document document, String recipeName, PdfFont boldFont) {
        Paragraph header = new Paragraph(recipeName)
                .setFont(boldFont)
                .setFontSize(24)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(20)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(header);
    }

    private void addSection(Document document, String title, String content, PdfFont boldFont, PdfFont regularFont) {

        Paragraph sectionTitle = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(55, 65, 81))
                .setMarginTop(15)
                .setMarginBottom(8);
        document.add(sectionTitle);

        Paragraph sectionContent = new Paragraph(content)
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(TEXT_GRAY)
                .setMarginBottom(10);
        document.add(sectionContent);
    }

    private void addIngredientsTable(Document document, List<RecipeComponentResponseDTO> components,
            PdfFont boldFont, PdfFont regularFont) {

        Paragraph title = new Paragraph("Ingredientes")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(55, 65, 81))
                .setMarginTop(15)
                .setMarginBottom(8);
        document.add(title);

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10);

        table.addHeaderCell(createHeaderCell("Producto", boldFont));
        table.addHeaderCell(createHeaderCell("Cantidad", boldFont));
        table.addHeaderCell(createHeaderCell("Subtotal", boldFont));

        for (RecipeComponentResponseDTO component : components) {
            table.addCell(createDataCell(sanitizePdfText(component.getProductName()), regularFont));
            table.addCell(createDataCell(String.format("%.2f", component.getQuantity()), regularFont));
            table.addCell(createDataCell(String.format("%.2f €", component.getSubtotal()), regularFont));
        }

        document.add(table);
    }

    private void addCostSection(Document document, BigDecimal totalCost, PdfFont boldFont, PdfFont regularFont) {

        Table costTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(15)
                .setBackgroundColor(ALLERGEN_BG);

        Cell labelCell = new Cell()
                .add(new Paragraph("Coste Total")
                        .setFont(boldFont)
                        .setFontSize(12)
                        .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setPadding(10)
                .setTextAlignment(TextAlignment.RIGHT);

        Cell valueCell = new Cell()
                .add(new Paragraph(String.format("%.2f €", totalCost))
                        .setFont(boldFont)
                        .setFontSize(12)
                        .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setPadding(10)
                .setTextAlignment(TextAlignment.RIGHT);

        costTable.addCell(labelCell);
        costTable.addCell(valueCell);

        document.add(costTable);
    }

    private void addElaborationSection(Document document, String elaboration, PdfFont boldFont, PdfFont regularFont) {

        Paragraph title = new Paragraph("Elaboración")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(55, 65, 81))
                .setMarginTop(15)
                .setMarginBottom(8);
        document.add(title);

        List<String> steps = parseElaborationSteps(elaboration);

        if (steps.size() > 1) {

            com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List()
                    .setSymbolIndent(12)
                    .setMarginBottom(10);

            for (String step : steps) {
                ListItem item = new ListItem();
                Paragraph p = new Paragraph(step)
                        .setFont(regularFont)
                        .setFontSize(10)
                        .setFontColor(TEXT_GRAY);
                item.add(p);
                list.add(item);
            }

            document.add(list);
        } else {

            Paragraph content = new Paragraph(elaboration)
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_GRAY)
                    .setMarginBottom(10);
            document.add(content);
        }
    }

    private void addAllergensSection(Document document, List<AllergenResponseDTO> allergens,
            PdfFont boldFont, PdfFont regularFont) {

        Paragraph title = new Paragraph("Alérgenos")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(55, 65, 81))
                .setMarginTop(15)
                .setMarginBottom(8);
        document.add(title);

        if (allergens == null || allergens.isEmpty()) {
            Paragraph noAllergens = new Paragraph("Esta receta no contiene alérgenos conocidos.")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(GREEN_TEXT)
                    .setItalic()
                    .setMarginBottom(10);
            document.add(noAllergens);
        } else {

            Paragraph allergensLine = new Paragraph()
                    .setMarginBottom(10);

            for (AllergenResponseDTO allergen : allergens) {
                Text allergenTag = new Text(" " + sanitizePdfText(allergen.getName()) + " ")
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
                .setPadding(8)
                .setTextAlignment(TextAlignment.LEFT);
    }

    private Cell createDataCell(String text, PdfFont regularFont) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(regularFont)
                        .setFontSize(9)
                        .setFontColor(TEXT_GRAY))
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.LEFT);
    }

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
