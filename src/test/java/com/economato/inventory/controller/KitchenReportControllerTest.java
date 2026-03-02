package com.economato.inventory.controller;

import com.economato.inventory.dto.request.ReportRange;
import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.service.KitchenReportPdfService;
import com.economato.inventory.service.KitchenReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenReportControllerTest {

    @Mock
    private KitchenReportService service;

    @Mock
    private KitchenReportPdfService pdfService;

    @InjectMocks
    private KitchenReportController controller;

    @Test
    void testGetReport_Daily() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("DAILY")
                .totalCookingSessions(5)
                .totalEstimatedCost(BigDecimal.valueOf(100))
                .build();

        when(service.generateReport(eq(ReportRange.DAILY), any(), any())).thenReturn(mockResponse);

        ResponseEntity<KitchenReportResponseDTO> response = controller.getReport(ReportRange.DAILY, null, null);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DAILY", response.getBody().getReportPeriod());
        assertEquals(5, response.getBody().getTotalCookingSessions());
    }

    @Test
    void testGetReport_CustomRange() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("CUSTOM")
                .totalCookingSessions(10)
                .build();

        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 12, 31);

        when(service.generateReport(eq(ReportRange.CUSTOM), eq(startDate), eq(endDate))).thenReturn(mockResponse);

        ResponseEntity<KitchenReportResponseDTO> response = controller.getReport(ReportRange.CUSTOM, startDate,
                endDate);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CUSTOM", response.getBody().getReportPeriod());
        assertEquals(10, response.getBody().getTotalCookingSessions());
    }

    @Test
    void testExportPdf() {
        KitchenReportResponseDTO mockResponse = KitchenReportResponseDTO.builder()
                .reportPeriod("WEEKLY")
                .build();
        byte[] mockPdfBytes = new byte[] { 1, 2, 3 };

        when(service.generateReport(eq(ReportRange.WEEKLY), any(), any())).thenReturn(mockResponse);
        when(pdfService.generateKitchenReportPdf(any())).thenReturn(mockPdfBytes);

        ResponseEntity<byte[]> response = controller.exportPdf(ReportRange.WEEKLY, null, null);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"reporte-cocina-weekly.pdf\"",
                response.getHeaders().getContentDisposition().toString());
    }
}
