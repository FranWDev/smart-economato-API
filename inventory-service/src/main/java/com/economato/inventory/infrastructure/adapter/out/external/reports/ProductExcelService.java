package com.economato.inventory.infrastructure.adapter.out.external.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.projection.ProductProjection;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

@Service
public class ProductExcelService {

    private static final int CHUNK_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final I18nService i18nService;

    public ProductExcelService(ProductRepository productRepository,
            ProductBatchRepository productBatchRepository,
            I18nService i18nService) {
        this.productRepository = productRepository;
        this.productBatchRepository = productBatchRepository;
        this.i18nService = i18nService;
    }

    /**
     * Genera el Excel de productos con streaming real:
     * - La BD se consulta en chunks de CHUNK_SIZE filas usando Slice (sin COUNT).
     * - SXSSFWorkbook mantiene solo CHUNK_SIZE filas activas en JVM; el resto se
     * vuelca a disco temporal comprimido (gzip).
     * - Se escribe directamente al OutputStream del cliente, sin
     * ByteArrayOutputStream
     * intermedio, de modo que la memoria usada es O(1) respecto al total de filas.
     */
    @Transactional(readOnly = true)
    public void streamProductsExcel(OutputStream out) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(CHUNK_SIZE)) {
            workbook.setCompressTempFiles(true);

            Sheet sheet = workbook.createSheet(i18nService.getMessage(MessageKey.EXCEL_SHEET_PRODUCTS));
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            String[] headers = {
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_ID),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_NAME),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_UNIT),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_UNIT_PRICE),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_BARCODE),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_STOCK_CURRENT),
                    i18nService.getMessage(MessageKey.REPORT_COLUMN_AVAILABILITY),
                        i18nService.getMessage(MessageKey.REPORT_COLUMN_SUPPLIER),
                        i18nService.getMessage(MessageKey.REPORT_COLUMN_NEAREST_EXPIRATION)
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            setColumnWidths(sheet);

            int rowIndex = 1;
            int page = 0;
            Slice<ProductProjection> slice;

            do {
                slice = productRepository.findByIsHiddenFalse(
                        PageRequest.of(page++, CHUNK_SIZE, Sort.by(Sort.Direction.ASC, "id")));

                for (ProductProjection p : slice.getContent()) {
                    Row row = sheet.createRow(rowIndex++);

                    createTextCell(row, 0, p.getId() == null ? "" : p.getId().toString(), bodyStyle);
                    createTextCell(row, 1, nullToEmpty(p.getName()), bodyStyle);
                    createTextCell(row, 2, nullToEmpty(p.getUnit()), bodyStyle);
                    createNumberCell(row, 3, p.getUnitPrice(), numberStyle);
                    createTextCell(row, 4, nullToEmpty(p.getProductCode()), bodyStyle);
                    createNumberCell(row, 5, p.getCurrentStock(), numberStyle);
                    createNumberCell(row, 6, p.getAvailabilityPercentage(), numberStyle);
                    createTextCell(row, 7,
                            p.getSupplier() == null ? "" : nullToEmpty(p.getSupplier().getName()),
                            bodyStyle);
                        createTextCell(row, 8, findNearestExpirationText(p.getId()), bodyStyle);
                }

                // Volcar filas ya procesadas a disco temporal para liberar heap
                ((SXSSFSheet) sheet).flushRows(CHUNK_SIZE);

            } while (slice.hasNext());

            workbook.write(out);
        }
    }

    private void setColumnWidths(Sheet sheet) {
        int[] widths = { 10, 30, 12, 18, 22, 16, 18, 26, 20 };
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private String findNearestExpirationText(Integer productId) {
        if (productId == null) {
            return "";
        }
        LocalDate nearest = productBatchRepository.findActiveByProductIdOrderByExpiration(productId).stream()
                .map(batch -> batch.getExpirationDate())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return nearest != null ? nearest.toString() : "";
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(SXSSFWorkbook workbook) {
        CellStyle style = createBodyStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void createTextCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("");
        }
        cell.setCellStyle(style);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
