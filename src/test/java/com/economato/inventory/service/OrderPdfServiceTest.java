package com.economato.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;

@ExtendWith(MockitoExtension.class)
class OrderPdfServiceTest {

    @InjectMocks
    private OrderPdfService orderPdfService;

    private OrderResponseDTO testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new OrderResponseDTO();
        testOrder.setId(1);
        testOrder.setUserId(10);
        testOrder.setUserName("Test User");
        testOrder.setOrderDate(LocalDateTime.of(2026, 2, 10, 12, 0));
        testOrder.setStatus("CREATED");
        testOrder.setTotalPrice(new BigDecimal("15.50"));

        List<OrderDetailResponseDTO> details = new ArrayList<>();
        details.add(createDetail(1, 1, "Product A", "2.0", "5.00"));
        details.add(createDetail(1, 2, "Product B", "1.5", "7.50"));
        testOrder.setDetails(details);
    }

    @Test
    void generateOrderPdf_WithCompleteOrder_ShouldGeneratePdf() throws Exception {
        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateOrderPdf_WithMinimalOrder_ShouldGeneratePdf() throws Exception {
        OrderResponseDTO minimalOrder = new OrderResponseDTO();
        minimalOrder.setId(2);
        minimalOrder.setUserId(20);
        minimalOrder.setUserName("Minimal User");
        minimalOrder.setTotalPrice(new BigDecimal("0.00"));
        minimalOrder.setDetails(new ArrayList<>());

        byte[] pdfBytes = orderPdfService.generateOrderPdf(minimalOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateOrderPdf_WithEmptyDetails_ShouldGeneratePdf() throws Exception {
        testOrder.setDetails(new ArrayList<>());

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithNullDetails_ShouldGeneratePdf() throws Exception {
        testOrder.setDetails(null);

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithLongUserName_ShouldGeneratePdf() throws Exception {
        testOrder.setUserName("User with a very long name that should not break the PDF layout or generation");

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithSpecialCharacters_ShouldGeneratePdf() throws Exception {
        testOrder.setUserName("User/Name: Example #1");
        testOrder.setStatus("IN_REVIEW");

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithZeroTotal_ShouldGeneratePdf() throws Exception {
        testOrder.setTotalPrice(BigDecimal.ZERO);

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithNullStatus_ShouldGeneratePdf() throws Exception {
        testOrder.setStatus(null);

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithNullOrderDate_ShouldGeneratePdf() throws Exception {
        testOrder.setOrderDate(null);

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateOrderPdf_WithManyDetails_ShouldGeneratePdf() throws Exception {
        List<OrderDetailResponseDTO> details = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            details.add(createDetail(1, i, "Product " + i, "1.00", "1.25"));
        }
        testOrder.setDetails(details);

        byte[] pdfBytes = orderPdfService.generateOrderPdf(testOrder);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    private OrderDetailResponseDTO createDetail(int orderId, int productId, String name,
            String quantity, String unitPrice) {
        OrderDetailResponseDTO detail = new OrderDetailResponseDTO();
        detail.setOrderId(orderId);
        detail.setProductId(productId);
        detail.setProductName(name);
        detail.setQuantity(new BigDecimal(quantity));
        detail.setUnitPrice(new BigDecimal(unitPrice));
        detail.setSubtotal(detail.getQuantity().multiply(detail.getUnitPrice()));
        return detail;
    }
}
