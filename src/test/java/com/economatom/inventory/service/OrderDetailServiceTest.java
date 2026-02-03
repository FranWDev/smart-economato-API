package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.OrderDetailRequestDTO;
import com.economatom.inventory.dto.response.OrderDetailResponseDTO;
import com.economatom.inventory.mapper.OrderDetailMapper;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderDetail;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.repository.OrderDetailRepository;
import com.economatom.inventory.repository.OrderRepository;
import com.economatom.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    }

    @Test
    void findByOrder_ShouldReturnOrderDetails() {

        when(repository.findByOrderId(1)).thenReturn(Arrays.asList(testOrderDetail));
        when(orderDetailMapper.toResponseDTO(testOrderDetail)).thenReturn(testOrderDetailResponseDTO);

        List<OrderDetailResponseDTO> result = orderDetailService.findByOrder(testOrder);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByOrderId(1);
    }

    @Test
    void findByOrder_WhenNoDetailsFound_ShouldReturnEmptyList() {

        testOrder.setId(999);
        when(repository.findByOrderId(999)).thenReturn(Arrays.asList());

        List<OrderDetailResponseDTO> result = orderDetailService.findByOrder(testOrder);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByOrderId(999);
    }

    @Test
    void findByProduct_ShouldReturnOrderDetails() {

        when(repository.findByProductId(1)).thenReturn(Arrays.asList(testOrderDetail));
        when(orderDetailMapper.toResponseDTO(testOrderDetail)).thenReturn(testOrderDetailResponseDTO);

        List<OrderDetailResponseDTO> result = orderDetailService.findByProduct(testProduct);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByProductId(1);
    }
}
