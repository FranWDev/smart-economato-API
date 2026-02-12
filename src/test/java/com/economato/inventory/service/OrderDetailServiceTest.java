package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.dto.projection.OrderDetailProjection;
import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.mapper.OrderDetailMapper;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderDetail;
import com.economato.inventory.model.Product;
import com.economato.inventory.repository.OrderDetailRepository;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDetailServiceTest {

    @Mock
    private OrderDetailRepository repository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @InjectMocks
    private OrderDetailService orderDetailService;

    private OrderDetail testOrderDetail;
    private OrderDetailRequestDTO testOrderDetailRequestDTO;
    private OrderDetailResponseDTO testOrderDetailResponseDTO;
    private Order testOrder;
    private Product testProduct;
    private OrderDetailProjection testProjection;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setStatus("CREATED");
        testOrder.setDetails(new ArrayList<>());

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setUnitPrice(new BigDecimal("10.0"));

        testOrderDetail = new OrderDetail();
        testOrderDetail.setOrder(testOrder);
        testOrderDetail.setProduct(testProduct);
        testOrderDetail.setQuantity(new BigDecimal("5.0"));

        testOrderDetailRequestDTO = new OrderDetailRequestDTO();
        testOrderDetailRequestDTO.setProductId(1);
        testOrderDetailRequestDTO.setQuantity(new BigDecimal("5.0"));

        testOrderDetailResponseDTO = new OrderDetailResponseDTO();
        testOrderDetailResponseDTO.setProductId(1);
        testOrderDetailResponseDTO.setQuantity(new BigDecimal("5.0"));

        // Setup Projection Mock
        testProjection = mock(OrderDetailProjection.class);
        lenient().when(testProjection.getQuantity()).thenReturn(new BigDecimal("5.0"));
        lenient().when(testProjection.getQuantityReceived()).thenReturn(BigDecimal.ZERO);

        OrderDetailProjection.OrderInfo orderInfo = mock(OrderDetailProjection.OrderInfo.class);
        lenient().when(orderInfo.getId()).thenReturn(1);
        lenient().when(testProjection.getOrder()).thenReturn(orderInfo);

        OrderDetailProjection.ProductInfo productInfo = mock(OrderDetailProjection.ProductInfo.class);
        lenient().when(productInfo.getId()).thenReturn(1);
        lenient().when(productInfo.getName()).thenReturn("Test Product");
        lenient().when(productInfo.getUnitPrice()).thenReturn(new BigDecimal("10.0"));
        lenient().when(testProjection.getProduct()).thenReturn(productInfo);
    }

    @Test
    void findByOrder_ShouldReturnOrderDetails() {

        when(repository.findProjectedByOrderId(1)).thenReturn(Arrays.asList(testProjection));

        List<OrderDetailResponseDTO> result = orderDetailService.findByOrder(testOrder);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getProductId());
        verify(repository).findProjectedByOrderId(1);
    }

    @Test
    void findByOrder_WhenNoDetailsFound_ShouldReturnEmptyList() {

        testOrder.setId(999);
        when(repository.findProjectedByOrderId(999)).thenReturn(Arrays.asList());

        List<OrderDetailResponseDTO> result = orderDetailService.findByOrder(testOrder);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findProjectedByOrderId(999);
    }

    @Test
    void findByProduct_ShouldReturnOrderDetails() {

        when(repository.findProjectedByProductId(1)).thenReturn(Arrays.asList(testProjection));

        List<OrderDetailResponseDTO> result = orderDetailService.findByProduct(testProduct);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getProductId());
        verify(repository).findProjectedByProductId(1);
    }
}
