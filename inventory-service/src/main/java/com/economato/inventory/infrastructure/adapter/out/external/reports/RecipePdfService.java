package com.economato.inventory.infrastructure.adapter.out.external.reports;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.response.AllergenResponseDTO;
import com.economato.inventory.application.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
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
    private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
    private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
    private static final Color SECTION_TITLE_COLOR = new DeviceRgb(55, 65, 81);
    private static final Color ROW_HOVER_BG = new DeviceRgb(243, 244, 246);
    private static final Color ALLERGEN_BG = new DeviceRgb(254, 242, 242);
    private static final Color ALLERGEN_TEXT = new DeviceRgb(220, 38, 38);
    private static final Color GREEN_TEXT = new DeviceRgb(16, 185, 129);

    private final I18nService i18nService;

    public RecipePdfService(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    public byte[] generateRecipePdf(RecipeResponseDTO recipe) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);

            PdfFont footerFont = PdfFontFactory.createFont("Helvetica");
            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler(footerFont));

            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 50, 40);

            PdfFont regularFont = footerFont;
            PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

            addHeader(document, sanitizePdfText(recipe.getName()), boldFont);

            if (recipe.getPresentation() != null && !recipe.getPresentation().isEmpty()) {
                addSection(document, i18nService.getMessage(MessageKey.REPORT_SECTION_PRESENTATION), sanitizePdfText(recipe.getPresentation()), boldFont, regularFont);
            }

            if (recipe.getElaboration() != null && !recipe.getElaboration().isEmpty()) {
                addElaborationSection(document, sanitizePdfText(recipe.getElaboration()), boldFont, regularFont);
            }

            if (recipe.getComponents() != null && !recipe.getComponents().isEmpty()) {
                addIngredientsTable(document, recipe.getComponents(), boldFont, regularFont);
            }

            addCostBanner(document, recipe.getTotalCost(), recipe.getSellingPrice(), boldFont);


            addAllergensSection(document, recipe.getAllergens(), boldFont, regularFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_REPORT_RECIPE_PDF_GENERATION, new Object[] { recipe.getId() }), e);
        }
    }

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

        Paragraph accent = new Paragraph("")
                .setBackgroundColor(SECONDARY_COLOR)
                .setPaddingTop(3)
                .setMarginTop(0)
                .setMarginBottom(24);
        document.add(accent);
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

    private void addElaborationSection(Document document, String elaboration, PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, i18nService.getMessage(MessageKey.REPORT_SECTION_ELABORATION), boldFont);

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

    private void addIngredientsTable(Document document, List<RecipeComponentResponseDTO> components,
            PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, i18nService.getMessage(MessageKey.REPORT_SECTION_INGREDIENTS), boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 }))

                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8)
                .setMarginBottom(8);

        table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_PRODUCT), boldFont));
        table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_GROSS_QUANTITY), boldFont));
        table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_QUANTITY), boldFont));
        table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_SUBTOTAL), boldFont));


        for (int i = 0; i < components.size(); i++) {
            RecipeComponentResponseDTO comp = components.get(i);
            Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

            table.addCell(createTableDataCell(sanitizePdfText(comp.getProductName()), boldFont, rowBg, true));
            
            BigDecimal grossQty = comp.getQuantity();
            if (comp.getAvailabilityPercentage() != null && comp.getAvailabilityPercentage().compareTo(BigDecimal.ZERO) > 0) {
                grossQty = comp.getQuantity().multiply(new BigDecimal("100"))
                        .divide(comp.getAvailabilityPercentage(), 2, RoundingMode.HALF_UP);
            }
            
            String unit = comp.getProductUnit() != null ? comp.getProductUnit() : "";
            table.addCell(createTableDataCell(String.format("%.2f %s", grossQty, unit).trim(), regularFont, rowBg, false));
            table.addCell(createTableDataCell(String.format("%.2f %s", comp.getQuantity(), unit).trim(), regularFont, rowBg, false));
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

    private void addCostBanner(Document document, BigDecimal totalCost, BigDecimal sellingPrice, PdfFont boldFont) {

        Table costTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(ALLERGEN_BG)
                .setMarginTop(4)
                .setMarginBottom(24);

        Cell labelCell = new Cell()
                .add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_TOTAL_COST))
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

        if (sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) > 0) {
            addSellingPriceBanner(document, sellingPrice, totalCost, boldFont);
        }
    }

    private void addSellingPriceBanner(Document document, BigDecimal sellingPrice, BigDecimal totalCost, PdfFont boldFont) {
        Table sellTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(new DeviceRgb(240, 253, 244)) // Green light
                .setMarginTop(-24)
                .setMarginBottom(24)
                .setBorder(new SolidBorder(GREEN_TEXT, 0.5f));

        BigDecimal margin = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            margin = sellingPrice.subtract(totalCost).multiply(new BigDecimal("100")).divide(sellingPrice, 2, RoundingMode.HALF_UP);
        }

        Cell labelCell = new Cell()
                .add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_SELLING_PRICE) + " (" + margin + "%)")
                        .setFont(boldFont)
                        .setFontSize(14)
                        .setFontColor(GREEN_TEXT))
                .setBorder(Border.NO_BORDER)
                .setPadding(14)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        Cell valueCell = new Cell()
                .add(new Paragraph(String.format("%.2f \u20ac", sellingPrice))
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(GREEN_TEXT)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setPadding(14)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        sellTable.addCell(labelCell);
        sellTable.addCell(valueCell);
        document.add(sellTable);
    }


    private void addAllergensSection(Document document, List<AllergenResponseDTO> allergens,
            PdfFont boldFont, PdfFont regularFont) {
        addSectionTitle(document, i18nService.getMessage(MessageKey.REPORT_SECTION_ALLERGENS), boldFont);

        if (allergens == null || allergens.isEmpty()) {
            Paragraph noAllergens = new Paragraph(i18nService.getMessage(MessageKey.REPORT_MESSAGE_NO_ALLERGENS))
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

    private class FooterEventHandler implements IEventHandler {
        private final PdfFont font;

        FooterEventHandler(PdfFont font) {
            this.font = font;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNum = pdf.getPageNumber(page);
            float pageWidth = page.getPageSize().getWidth();

            try {
                PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);

                try (Canvas c = new Canvas(canvas, new Rectangle(0, 18, pageWidth, 16))) {
                    c.add(new Paragraph(String.valueOf(pageNum))
                            .setFont(font)
                            .setFontSize(8)
                            .setFontColor(ColorConstants.BLACK)
                            .setTextAlignment(TextAlignment.CENTER));
                }

                try (Canvas c = new Canvas(canvas, new Rectangle(0, 4, pageWidth, 14))) {
                    c.add(new Paragraph(i18nService.getMessage(MessageKey.GENERAL_POWERED_BY))
                            .setFont(font)
                            .setFontSize(7)
                            .setFontColor(TEXT_GRAY)
                            .setTextAlignment(TextAlignment.CENTER));
                }

                canvas.release();
            } catch (Exception ignored) {
            }
        }
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
