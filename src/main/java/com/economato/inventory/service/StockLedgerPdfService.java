package com.economato.inventory.service;

import com.economato.inventory.dto.response.IntegrityCheckResult;
import com.economato.inventory.dto.response.LedgerPdfResponseDTO;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;
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
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StockLedgerPdfService {

    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(59, 130, 246);
    private static final DeviceRgb SECONDARY_COLOR = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(229, 231, 235);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb SIGNATURE_BG = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb VERIFIED_COLOR = new DeviceRgb(16, 185, 129);
    private static final DeviceRgb UNVERIFIED_COLOR = new DeviceRgb(239, 68, 68);

    private final StockLedgerRepository stockLedgerRepository;
    private final ProductRepository productRepository;
    private final I18nService i18nService;
    private final StockLedgerService stockLedgerService;

    public StockLedgerPdfService(StockLedgerRepository stockLedgerRepository,
            ProductRepository productRepository,
            I18nService i18nService,
            StockLedgerService stockLedgerService) {
        this.stockLedgerRepository = stockLedgerRepository;
        this.productRepository = productRepository;
        this.i18nService = i18nService;
        this.stockLedgerService = stockLedgerService;
    }

    public byte[] generateStockLedgerPdf(Integer productId) {
        try {
            var product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));

            List<StockLedger> ledgerEntries = stockLedgerRepository
                    .findByProductIdOrderBySequenceNumber(productId);

            IntegrityCheckResult integrityResult = stockLedgerService.verifyChainIntegrity(productId);
            Set<Long> corruptedSequences = extractCorruptedSequences(integrityResult);

            if (ledgerEntries.isEmpty()) {
                throw new ResourceNotFoundException("No hay registros de ledger para este producto");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);

            PdfFont footerFont = PdfFontFactory.createFont("Helvetica");
            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new LedgerFooterEventHandler(footerFont));

            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 50, 40);

            PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regularFont = footerFont;

            // Header
            addHeader(document, "LEDGER DE STOCK - " + product.getName(), boldFont);

            // Product info
            addProductInfoSection(document, product, boldFont, regularFont);

            // Ledger table
            addLedgerTable(document, ledgerEntries, corruptedSequences, boldFont, regularFont);

            // Signature & authentication section
            String contentHash = generateContentHash(ledgerEntries);
            addAuthenticationSignature(document, ledgerEntries, integrityResult, corruptedSequences, contentHash,
                    boldFont, regularFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar PDF del ledger para producto {}", productId, e);
            throw new RuntimeException("Error al generar el PDF del ledger", e);
        }
    }

    /**
     * Genera el PDF del ledger con información adicional sobre la integridad de la cadena.
     * Verifica automáticamente la integridad y devuelve el resultado junto con el PDF.
     * 
     * @param productId ID del producto
     * @return DTO con el PDF y la información de integridad
     */
    public LedgerPdfResponseDTO generateStockLedgerPdfWithIntegrity(Integer productId) {
        // Generar el PDF
        byte[] pdfContent = generateStockLedgerPdf(productId);
        
        // Verificar la integridad de la cadena
        IntegrityCheckResult integrityResult = stockLedgerService.verifyChainIntegrity(productId);
        
        log.info("PDF generado para producto {}: {} bytes. Integridad: {}", 
                 productId, pdfContent.length, integrityResult.isValid() ? "VÁLIDA" : "CORRUPTA");
        
        return new LedgerPdfResponseDTO(
                pdfContent,
                integrityResult.isValid(),
                integrityResult.getMessage(),
                integrityResult.getErrors()
        );
    }

    /**
     * Verifica la integridad del hash de un ledger comparándolo con el contenido.
     * Útil para pruebas forenses/legales.
     */
    public boolean verifyLedgerIntegrity(Integer productId, String providedHash) {
        List<StockLedger> ledgerEntries = stockLedgerRepository
                .findByProductIdOrderBySequenceNumber(productId);

        if (ledgerEntries.isEmpty()) {
            return false;
        }

        String computedHash = generateContentHash(ledgerEntries);
        boolean isValid = computedHash.equalsIgnoreCase(providedHash);
        
        log.info("Verificación de integridad para producto {}: {} (hash proporcionado: {}, calculado: {})",
                productId, isValid ? "VÁLIDO" : "INVÁLIDO", providedHash, computedHash);

        return isValid;
    }

    /**
     * Genera un hash SHA-256 del contenido del ledger para verificación de integridad.
     */
    private String generateContentHash(List<StockLedger> ledgerEntries) {
        try {
            StringBuilder content = new StringBuilder();
            for (StockLedger entry : ledgerEntries) {
                content.append(entry.getSequenceNumber())
                        .append("|").append(entry.getMovementType())
                        .append("|").append(entry.getQuantityDelta())
                        .append("|").append(entry.getResultingStock())
                        .append("|").append(entry.getTransactionTimestamp())
                        .append("|").append(entry.getDescription())
                        .append("|").append(entry.getCurrentHash())
                        .append("||");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hash) {
                hexHash.append(String.format("%02x", b));
            }
            return hexHash.toString();
        } catch (Exception e) {
            log.error("Error al generar hash de contenido", e);
            throw new RuntimeException("Error al generar hash", e);
        }
    }

    private void addHeader(Document document, String title, PdfFont boldFont) {
        Paragraph header = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(18)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(20)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(0);
        document.add(header);

        Paragraph accent = new Paragraph("")
                .setBackgroundColor(SECONDARY_COLOR)
                .setPaddingTop(2)
                .setMarginTop(0)
                .setMarginBottom(20);
        document.add(accent);
    }

    private void addProductInfoSection(Document document, com.economato.inventory.model.Product product,
            PdfFont boldFont, PdfFont regularFont) {
        Paragraph productTitle = new Paragraph("INFORMACIÓN DEL PRODUCTO")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(8)
                .setPaddingBottom(4)
                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2));
        document.add(productTitle);

        Table infoTable = new Table(2);
        infoTable.setWidth(UnitValue.createPercentValue(100));
        infoTable.setMarginBottom(20);

        addInfoRow(infoTable, "Nombre", product.getName(), boldFont, regularFont);
        addInfoRow(infoTable, "Código", product.getProductCode(), boldFont, regularFont);
        addInfoRow(infoTable, "Tipo", product.getType(), boldFont, regularFont);
        addInfoRow(infoTable, "Stock Actual", product.getCurrentStock().toString() + " " + product.getUnit(),
                boldFont, regularFont);
        addInfoRow(infoTable, "Precio Unitario", "$" + product.getUnitPrice().toString(), boldFont, regularFont);

        document.add(infoTable);
    }

    private void addInfoRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(boldFont).setFontSize(10).setFontColor(TEXT_GRAY))
                .setBackgroundColor(SIGNATURE_BG)
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1));

        Cell valueCell = new Cell()
                .add(new Paragraph(value).setFont(regularFont).setFontSize(10).setFontColor(TEXT_DARK))
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1));

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addLedgerTable(Document document, List<StockLedger> ledgerEntries, Set<Long> corruptedSequences,
            PdfFont boldFont,
            PdfFont regularFont) {
        Paragraph tableTitle = new Paragraph("HISTORIAL DE TRANSACCIONES")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(8)
                .setPaddingBottom(4)
                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2));
        document.add(tableTitle);

        float[] columnWidths = { 0.9f, 1.4f, 1.1f, 1.3f, 1.6f, 3.0f, 1.0f };
        Table table = new Table(columnWidths);
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(20);

        // Header row
        addHeaderCell(table, "#", boldFont);
        addHeaderCell(table, "Tipo", boldFont);
        addHeaderCell(table, "Cantidad", boldFont);
        addHeaderCell(table, "Stock Resultado", boldFont);
        addHeaderCell(table, "Fecha/Hora", boldFont);
        addHeaderCell(table, "Descripción / Usuario", boldFont);
        addHeaderCell(table, "Verif.", boldFont);

        // Data rows
        for (int i = 0; i < ledgerEntries.size(); i++) {
            StockLedger entry = ledgerEntries.get(i);
            boolean isEven = i % 2 == 0;
            boolean isCorrupted = corruptedSequences.contains(entry.getSequenceNumber());

            addDataCell(table, "#" + entry.getSequenceNumber(), regularFont, isEven);
            addDataCell(table, entry.getMovementType().toString(), regularFont, isEven);
            addDataCell(table, entry.getQuantityDelta().toString(), regularFont, isEven);
            addDataCell(table, entry.getResultingStock().toString(), regularFont, isEven);
            addDataCell(table,
                    entry.getTransactionTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    regularFont, isEven);

            String description = entry.getDescription() != null && !entry.getDescription().isBlank()
                    ? entry.getDescription()
                    : "-";
            String userName = entry.getUser() != null && entry.getUser().getName() != null
                    ? entry.getUser().getName()
                    : "Sistema";

            addDataCell(table, description + "\npor " + userName, regularFont, isEven);
            addDataCell(table, isCorrupted ? "CORRUPTA" : "OK", regularFont, isEven,
                    isCorrupted ? UNVERIFIED_COLOR : VERIFIED_COLOR);
        }

        document.add(table);
    }

    private void addHeaderCell(Table table, String text, PdfFont font) {
        Cell cell = new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(8)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell);
    }

    private void addDataCell(Table table, String text, PdfFont font, boolean isEven) {
        Cell cell = new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8).setFontColor(TEXT_DARK))
                .setBackgroundColor(isEven ? ColorConstants.WHITE : SIGNATURE_BG)
                .setPadding(6)
                .setBorder(new SolidBorder(BORDER_COLOR, 1));
        table.addCell(cell);
    }

        private void addDataCell(Table table, String text, PdfFont font, boolean isEven, DeviceRgb textColor) {
                Cell cell = new Cell()
                                .add(new Paragraph(text).setFont(font).setFontSize(8).setFontColor(textColor))
                                .setBackgroundColor(isEven ? ColorConstants.WHITE : SIGNATURE_BG)
                                .setPadding(6)
                                .setBorder(new SolidBorder(BORDER_COLOR, 1));
                table.addCell(cell);
        }

        private void addAuthenticationSignature(Document document, List<StockLedger> ledgerEntries,
                        IntegrityCheckResult integrityResult, Set<Long> corruptedSequences, String contentHash,
                        PdfFont boldFont, PdfFont regularFont) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        Paragraph signatureTitle = new Paragraph("FIRMA DE AUTENTICIDAD")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(TEXT_DARK)
                .setMarginBottom(8)
                .setPaddingBottom(4)
                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2));
        document.add(signatureTitle);

        Table signatureTable = new Table(1);
        signatureTable.setWidth(UnitValue.createPercentValue(100));
        signatureTable.setMarginBottom(20);

        // Hash verification status
        StockLedger lastEntry = ledgerEntries.get(ledgerEntries.size() - 1);
        boolean chainValid = integrityResult != null && integrityResult.isValid();

        Cell statusCell = new Cell()
                .add(new Paragraph("Estado de Integridad de Cadena: ")
                        .setFont(boldFont)
                        .setFontSize(10)
                        .setFontColor(TEXT_DARK)
                        .add(new com.itextpdf.layout.element.Text(chainValid ? "✓ ÍNTEGRA" : "⚠ CORRUPTA")
                                .setFontColor(chainValid ? VERIFIED_COLOR : UNVERIFIED_COLOR)
                                .setFont(boldFont)))
                .setPadding(12)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setBackgroundColor(new DeviceRgb(245, 245, 245));
        signatureTable.addCell(statusCell);

        String corruptedSummary = corruptedSequences.isEmpty()
                ? "Ninguna"
                : corruptedSequences.stream()
                        .sorted()
                        .map(seq -> "#" + seq)
                        .collect(java.util.stream.Collectors.joining(", "));

        Cell corruptedCell = new Cell()
                .add(new Paragraph("Transacciones corruptas detectadas: ")
                        .setFont(boldFont)
                        .setFontSize(9)
                        .setFontColor(TEXT_GRAY)
                        .add(new com.itextpdf.layout.element.Text(corruptedSummary)
                                .setFont(chainValid ? regularFont : boldFont)
                                .setFontColor(chainValid ? TEXT_DARK : UNVERIFIED_COLOR)))
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setBackgroundColor(SIGNATURE_BG);
        signatureTable.addCell(corruptedCell);

        // Hash details
        Cell hashLabelCell = new Cell()
                .add(new Paragraph("SHA-256 Hash del Contenido:")
                        .setFont(boldFont)
                        .setFontSize(9)
                        .setFontColor(TEXT_GRAY))
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setBackgroundColor(SIGNATURE_BG);
        signatureTable.addCell(hashLabelCell);

        Cell hashValueCell = new Cell()
                .add(new Paragraph(contentHash)
                        .setFont(boldFont)
                        .setFontSize(7)
                        .setFontColor(TEXT_DARK)
                        .setFixedLeading(10f))
                .setPadding(10)
                .setBorder(new SolidBorder(BORDER_COLOR, 1));
        signatureTable.addCell(hashValueCell);

        // Timestamp
        LocalDateTime generatedAt = LocalDateTime.now();
        String formattedTimestamp = generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        Cell timestampCell = new Cell()
                .add(new Paragraph("Generado: ").setFont(boldFont).setFontSize(9).setFontColor(TEXT_GRAY)
                        .add(new com.itextpdf.layout.element.Text(formattedTimestamp).setFont(regularFont)))
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setBackgroundColor(SIGNATURE_BG);
        signatureTable.addCell(timestampCell);

        // Last hash reference
        Cell lastHashCell = new Cell()
                .add(new Paragraph("Hash Último Registro: ")
                        .setFont(boldFont)
                        .setFontSize(8)
                        .setFontColor(TEXT_GRAY)
                        .add(new com.itextpdf.layout.element.Text(lastEntry.getCurrentHash())
                                .setFont(boldFont)
                                .setFontSize(7)))
                .setPadding(8)
                .setBorder(new SolidBorder(BORDER_COLOR, 1));
        signatureTable.addCell(lastHashCell);

        document.add(signatureTable);

        // Legal notice
        Paragraph legalNotice = new Paragraph(
                "Este documento es una prueba de integridad de datos. "
                        +
                        "La verificación se puede validar calculando el hash SHA-256 del contenido y comparando con el valor mostrado. "
                        +
                        "Cualquier alteración de los datos resultará en un hash diferente.")
                .setFont(regularFont)
                .setFontSize(7)
                .setFontColor(TEXT_GRAY)
                .setFixedLeading(10f)
                .setMarginTop(12)
                .setPadding(10)
                .setBackgroundColor(new DeviceRgb(255, 253, 208))
                .setBorder(new SolidBorder(new DeviceRgb(217, 119, 6), 1));
        document.add(legalNotice);
    }

        private Set<Long> extractCorruptedSequences(IntegrityCheckResult integrityResult) {
                Set<Long> sequences = new HashSet<>();
                if (integrityResult == null || integrityResult.getErrors() == null) {
                        return sequences;
                }

                Pattern txPattern = Pattern.compile("TX#(\\d+)");
                for (String error : integrityResult.getErrors()) {
                        if (error == null) {
                                continue;
                        }
                        Matcher matcher = txPattern.matcher(error);
                        if (matcher.find()) {
                                sequences.add(Long.parseLong(matcher.group(1)));
                        }
                }
                return sequences;
        }

    /**
     * Footer handler para mostrar número de página y datos de auditoría.
     */
    private static class LedgerFooterEventHandler implements IEventHandler {
        private PdfFont footerFont;

        public LedgerFooterEventHandler(PdfFont font) {
            this.footerFont = font;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            PdfDocument pdfDoc = docEvent.getDocument();

            Rectangle pageSize = page.getPageSize();
            Canvas canvas = new Canvas(new com.itextpdf.kernel.pdf.canvas.PdfCanvas(page), pageSize);

            int pageNum = pdfDoc.getPageNumber(page);
            String footer = "Página " + pageNum + " | Documento confidencial - Ledger de Stock";

            canvas.showTextAligned(new Paragraph(footer)
                    .setFont(footerFont)
                    .setFontSize(8)
                    .setFontColor(new DeviceRgb(128, 128, 128)),
                    pageSize.getWidth() / 2,
                    20,
                    TextAlignment.CENTER);

            canvas.close();
        }
    }
}
