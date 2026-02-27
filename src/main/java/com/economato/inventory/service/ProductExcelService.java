package com.economato.inventory.service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;

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

import com.economato.inventory.dto.projection.ProductProjection;
import com.economato.inventory.repository.ProductRepository;

@Service
public class ProductExcelService {

    private static final int CHUNK_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductExcelService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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

            Sheet sheet = workbook.createSheet("Productos");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            // Fila de cabecera
            String[] headers = {
                    "ID", "Nombre", "Tipo", "Unidad", "Precio Unitario",
                    "Código de Barras", "Stock Actual", "Stock Mínimo", "% Disponibilidad", "Proveedor"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            setColumnWidths(sheet);

            // Paginación por chunks — Slice evita la COUNT query de Page
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
                    createTextCell(row, 2, nullToEmpty(p.getType()), bodyStyle);
                    createTextCell(row, 3, nullToEmpty(p.getUnit()), bodyStyle);
                    createNumberCell(row, 4, p.getUnitPrice(), numberStyle);
                    createTextCell(row, 5, nullToEmpty(p.getProductCode()), bodyStyle);
                    createNumberCell(row, 6, p.getCurrentStock(), numberStyle);
                    createNumberCell(row, 7, p.getMinimumStock(), numberStyle);
                    createNumberCell(row, 8, p.getAvailabilityPercentage(), numberStyle);
                    createTextCell(row, 9,
                            p.getSupplier() == null ? "" : nullToEmpty(p.getSupplier().getName()),
                            bodyStyle);
                }

                // Volcar filas ya procesadas a disco temporal para liberar heap
                ((SXSSFSheet) sheet).flushRows(CHUNK_SIZE);

            } while (slice.hasNext());

            workbook.write(out);
        }
    }

    private void setColumnWidths(Sheet sheet) {
        int[] widths = { 10, 30, 18, 12, 18, 22, 16, 16, 18, 26 };
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
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
