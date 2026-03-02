package com.economato.inventory.controller;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.service.KitchenReportPdfService;
import com.economato.inventory.service.KitchenReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenReportControllerTest {

    @Mock
    private KitchenReportService service;

    /** Real instance - not mocked. iText runs for real so PDF errors are caught here. */
    @Spy
    private KitchenReportPdfService pdfService = new KitchenReportPdfService();

    @InjectMocks
    private KitchenReportController controller;

    @Test
    void testGetReport_Daily() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("02/03/2026 - 02/03/2026")
                .totalCookingSessions(5)
                .totalEstimatedCost(BigDecimal.valueOf(100))
                .build();

        when(service.generateReport(eq(ReportRange.DAILY), any(), any())).thenReturn(mockResponse);

        ResponseEntity<KitchenReportResponseDTO> response = controller.getReport(ReportRange.DAILY, null, null);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("02/03/2026 - 02/03/2026", response.getBody().getReportPeriod());
        assertEquals(5, response.getBody().getTotalCookingSessions());
    }

    @Test
    void testGetReport_CustomRange() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("01/01/2023 - 31/12/2023")
                .totalCookingSessions(10)
                .build();

        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 12, 31);

        when(service.generateReport(eq(ReportRange.CUSTOM), eq(startDate), eq(endDate))).thenReturn(mockResponse);

        ResponseEntity<KitchenReportResponseDTO> response = controller.getReport(ReportRange.CUSTOM, startDate, endDate);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("01/01/2023 - 31/12/2023", response.getBody().getReportPeriod());
        assertEquals(10, response.getBody().getTotalCookingSessions());
    }

    @Test
    void testExportPdf_generatesRealPdf() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("02/03/2026 - 08/03/2026")
                .totalCookingSessions(12)
                .totalPortionsCooked(BigDecimal.valueOf(36))
                .distinctRecipesCooked(3)
                .distinctUsersCooking(2)
                .distinctProductsUsed(5)
                .totalEstimatedCost(BigDecimal.valueOf(198.50))
                .topRecipes(Collections.emptyList())
                .topUsers(Collections.emptyList())
                .topProducts(Collections.emptyList())
                .build();

        when(service.generateReport(eq(ReportRange.WEEKLY), any(), any())).thenReturn(mockResponse);

        ResponseEntity<byte[]> response = controller.exportPdf(ReportRange.WEEKLY, null, null);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());

        String disposition = response.getHeaders().getContentDisposition().toString();
        assertTrue(disposition.startsWith("attachment; filename=\"reporte-cocina-"), "Unexpected: " + disposition);
        assertTrue(disposition.endsWith(".pdf\""), "Expected .pdf ending: " + disposition);

        byte[] pdfBytes = response.getBody();
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "Expected real PDF, got: " + pdfBytes.length + " bytes");
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }
}
