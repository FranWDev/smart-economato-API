package com.economato.inventory.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

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
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import com.economato.inventory.dto.response.ProductResponseDTO;

@Service
public class ProductExcelService {

    public byte[] generateProductsExcel(List<ProductResponseDTO> products) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);

            Sheet sheet = workbook.createSheet("Productos");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            String[] headers = {
                "ID", "Nombre", "Tipo", "Unidad", "Precio Unitario",
                "Codigo de Barras", "Stock Actual", "Proveedor"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ProductResponseDTO product : products) {
                Row row = sheet.createRow(rowIndex++);

                createTextCell(row, 0, product.getId() == null ? "" : product.getId().toString(), bodyStyle);
                createTextCell(row, 1, nullToEmpty(product.getName()), bodyStyle);
                createTextCell(row, 2, nullToEmpty(product.getType()), bodyStyle);
                createTextCell(row, 3, nullToEmpty(product.getUnit()), bodyStyle);
                createNumberCell(row, 4, product.getUnitPrice(), numberStyle);
                createTextCell(row, 5, nullToEmpty(product.getProductCode()), bodyStyle);
                createNumberCell(row, 6, product.getCurrentStock(), numberStyle);
                createTextCell(row, 7,
                        product.getSupplier() == null ? "" : nullToEmpty(product.getSupplier().getName()),
                        bodyStyle);
            }

            setColumnWidths(sheet);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setColumnWidths(Sheet sheet) {
        int[] widths = {10, 30, 18, 12, 18, 22, 16, 26};
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
