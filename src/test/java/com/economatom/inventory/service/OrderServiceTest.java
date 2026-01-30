package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.OrderDetailRequestDTO;
import com.economatom.inventory.dto.request.OrderReceptionDetailRequestDTO;
import com.economatom.inventory.dto.request.OrderReceptionRequestDTO;
import com.economatom.inventory.dto.request.OrderRequestDTO;
import com.economatom.inventory.dto.response.OrderResponseDTO;
import com.economatom.inventory.dto.response.UserResponseDTO;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.exception.ResourceNotFoundException;
import com.economatom.inventory.mapper.OrderMapper;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderDetail;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.OrderRepository;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderRequestDTO testOrderRequestDTO;
    private OrderResponseDTO testOrderResponseDTO;
    private User testUser;
    private Product testProduct;
    private UserResponseDTO testUserResponseDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        testUser.setEmail("test@test.com");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setCurrentStock(new BigDecimal("10.0"));

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setUsers(testUser);
        testOrder.setOrderDate(LocalDateTime.now());
        testOrder.setStatus("CREATED");
        testOrder.setDetails(new ArrayList<>());

        OrderDetail detail = new OrderDetail();
        detail.setOrder(testOrder);
        detail.setProduct(testProduct);
        detail.setQuantity(new BigDecimal("5.0"));
        testOrder.getDetails().add(detail);

        testOrderRequestDTO = new OrderRequestDTO();
        testOrderRequestDTO.setUserId(1);
        
        OrderDetailRequestDTO detailDTO = new OrderDetailRequestDTO();
        detailDTO.setProductId(1);
        detailDTO.setQuantity(new BigDecimal("5.0"));
        testOrderRequestDTO.setDetails(Arrays.asList(detailDTO));

        testOrderResponseDTO = new OrderResponseDTO();
        testOrderResponseDTO.setId(1);
        testOrderResponseDTO.setUserId(1);
        testOrderResponseDTO.setStatus("CREATED");

        testUserResponseDTO = new UserResponseDTO();
        testUserResponseDTO.setId(1);
        testUserResponseDTO.setName("Test User");
    }

    @Test
    void findAll_ShouldReturnListOfOrders() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(Arrays.asList(testOrder));
        when(repository.findAll(pageable)).thenReturn(page);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        List<OrderResponseDTO> result = orderService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findAll(pageable);
    }

    @Test
    void findById_WhenOrderExists_ShouldReturnOrder() {
        // Arrange
        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        Optional<OrderResponseDTO> result = orderService.findById(1);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testOrderResponseDTO.getId(), result.get().getId());
        verify(repository).findByIdWithDetails(1);
    }

    @Test
    void findById_WhenOrderDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        // Act
        Optional<OrderResponseDTO> result = orderService.findById(999);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findByIdWithDetails(999);
    }

    @Test
    void save_WhenValidOrder_ShouldCreateOrder() {
        // Arrange
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        OrderResponseDTO result = orderService.save(testOrderRequestDTO);

        // Assert
        assertNotNull(result);
        verify(userRepository).findById(1);
        verify(productRepository).findById(1);
        verify(repository).save(any(Order.class));
    }

    @Test
    void save_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.save(testOrderRequestDTO);
        });
        verify(userRepository).findById(1);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void save_WhenProductNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.save(testOrderRequestDTO);
        });
        verify(productRepository).findById(1);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void update_WhenOrderExists_ShouldUpdateOrder() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.saveAndFlush(any(Order.class))).thenReturn(testOrder);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        Optional<OrderResponseDTO> result = orderService.update(1, testOrderRequestDTO);

        // Assert
        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(any(Order.class));
    }

    @Test
    void update_WhenOrderDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        Optional<OrderResponseDTO> result = orderService.update(999, testOrderRequestDTO);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void deleteById_ShouldCallRepository() {
        // Arrange
        doNothing().when(repository).deleteById(1);

        // Act
        orderService.deleteById(1);

        // Assert
        verify(repository).deleteById(1);
    }

    @Test
    void findByUser_ShouldReturnUserOrders() {
        // Arrange
        when(repository.findByUserIdWithDetails(1)).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        List<OrderResponseDTO> result = orderService.findByUser(testUserResponseDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByUserIdWithDetails(1);
    }

    @Test
    void findByStatus_ShouldReturnOrdersWithStatus() {
        // Arrange
        when(repository.findByStatusWithDetails("CREATED")).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        List<OrderResponseDTO> result = orderService.findByStatus("CREATED");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByStatusWithDetails("CREATED");
    }

    @Test
    void findByDateRange_ShouldReturnOrdersInRange() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(repository.findByOrderDateBetweenWithDetails(start, end)).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        List<OrderResponseDTO> result = orderService.findByDateRange(start, end);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByOrderDateBetweenWithDetails(start, end);
    }

    @Test
    void receiveOrder_WhenValidReception_ShouldUpdateStockAndStatus() {
        // Arrange
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");
        
        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(1);
        receptionItem.setQuantityReceived(new BigDecimal("5.0"));
        receptionData.setItems(Arrays.asList(receptionItem));

        testOrder.getDetails().get(0).setQuantityReceived(new BigDecimal("5.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findByIdForUpdate(1)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        // Assert
        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findByIdForUpdate(1);
        verify(productRepository).save(testProduct);
        verify(repository).save(testOrder);
    }

    @Test
    void receiveOrder_WhenOrderNotFound_ShouldThrowException() {
        // Arrange
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(999);
        
        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        verify(repository).findByIdWithDetails(999);
    }

    @Test
    void receiveOrder_WhenQuantityLessThanOrdered_ShouldThrowException() {
        // Arrange
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");
        
        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(1);
        receptionItem.setQuantityReceived(new BigDecimal("3.0")); // Less than ordered (5.0)
        receptionData.setItems(Arrays.asList(receptionItem));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        assertTrue(exception.getMessage().contains("No se puede recibir menos cantidad"));
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void receiveOrder_WhenProductNotInOrder_ShouldThrowException() {
        // Arrange
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");
        
        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(999); // Product not in order
        receptionItem.setQuantityReceived(new BigDecimal("5.0"));
        receptionData.setItems(Arrays.asList(receptionItem));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void findPendingReception_ShouldReturnPendingOrders() {
        // Arrange
        when(repository.findByStatusWithDetails("PENDING")).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        List<OrderResponseDTO> result = orderService.findPendingReception();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByStatusWithDetails("PENDING");
    }

    @Test
    void updateStatus_WhenValidStatus_ShouldUpdateOrder() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        // Act
        Optional<OrderResponseDTO> result = orderService.updateStatus(1, "COMPLETED");

        // Assert
        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(testOrder);
    }

    @Test
    void updateStatus_WhenInvalidStatus_ShouldThrowException() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.updateStatus(1, "INVALID_STATUS");
        });
        assertTrue(exception.getMessage().contains("Estado de orden inválido"));
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_WithAllValidStatuses_ShouldSucceed() {
        // Arrange
        List<String> validStatuses = Arrays.asList("CREATED", "PENDING", "REVIEW", "COMPLETED", "INCOMPLETE");
        
        for (String status : validStatuses) {
            when(repository.findById(1)).thenReturn(Optional.of(testOrder));
            when(repository.save(testOrder)).thenReturn(testOrder);
            when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

            // Act
            Optional<OrderResponseDTO> result = orderService.updateStatus(1, status);

            // Assert
            assertTrue(result.isPresent());
        }
        
        verify(repository, times(validStatuses.size())).save(testOrder);
    }

    @Test
    void updateStatus_WhenOrderDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        Optional<OrderResponseDTO> result = orderService.updateStatus(999, "COMPLETED");

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Order.class));
    }
}
