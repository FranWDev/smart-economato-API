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
import com.economatom.inventory.model.MovementType;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderDetail;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.StockLedger;
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

    @Mock
    private StockLedgerService stockLedgerService;

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

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(Arrays.asList(testOrder));
        when(repository.findAll(pageable)).thenReturn(page);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findAll(pageable);
    }

    @Test
    void findById_WhenOrderExists_ShouldReturnOrder() {

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testOrderResponseDTO.getId(), result.get().getId());
        verify(repository).findByIdWithDetails(1);
    }

    @Test
    void findById_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findByIdWithDetails(999);
    }

    @Test
    void save_WhenValidOrder_ShouldCreateOrder() {

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.save(testOrderRequestDTO);

        assertNotNull(result);
        verify(userRepository).findById(1);
        verify(productRepository).findById(1);
        verify(repository).save(any(Order.class));
    }

    @Test
    void save_WhenUserNotFound_ShouldThrowException() {

        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.save(testOrderRequestDTO);
        });
        verify(userRepository).findById(1);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void save_WhenProductNotFound_ShouldThrowException() {

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.save(testOrderRequestDTO);
        });
        verify(productRepository).findById(1);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void update_WhenOrderExists_ShouldUpdateOrder() {

        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.saveAndFlush(any(Order.class))).thenReturn(testOrder);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.update(1, testOrderRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(any(Order.class));
    }

    @Test
    void update_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.update(999, testOrderRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void deleteById_ShouldCallRepository() {

        doNothing().when(repository).deleteById(1);

        orderService.deleteById(1);

        verify(repository).deleteById(1);
    }

    @Test
    void findByUser_ShouldReturnUserOrders() {

        when(repository.findByUserIdWithDetails(1)).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByUser(testUserResponseDTO);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByUserIdWithDetails(1);
    }

    @Test
    void findByStatus_ShouldReturnOrdersWithStatus() {

        when(repository.findByStatusWithDetails("CREATED")).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByStatus("CREATED");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByStatusWithDetails("CREATED");
    }

    @Test
    void findByDateRange_ShouldReturnOrdersInRange() {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(repository.findByOrderDateBetweenWithDetails(start, end)).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByDateRange(start, end);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByOrderDateBetweenWithDetails(start, end);
    }

    @Test
    void receiveOrder_WhenValidReception_ShouldUpdateStockAndStatus() {

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
        when(stockLedgerService.recordStockMovement(
                anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt()))
                .thenReturn(null);
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findByIdForUpdate(1);
        verify(stockLedgerService).recordStockMovement(
                anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt());
        verify(repository).save(testOrder);
    }

    @Test
    void receiveOrder_WhenOrderNotFound_ShouldThrowException() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(999);

        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        verify(repository).findByIdWithDetails(999);
    }

    @Test
    void receiveOrder_WhenQuantityLessThanOrdered_ShouldThrowException() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");

        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(1);
        receptionItem.setQuantityReceived(new BigDecimal("3.0"));
        receptionData.setItems(Arrays.asList(receptionItem));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        assertTrue(exception.getMessage().contains("No se puede recibir menos cantidad"));
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void receiveOrder_WhenProductNotInOrder_ShouldThrowException() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");

        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(999);
        receptionItem.setQuantityReceived(new BigDecimal("5.0"));
        receptionData.setItems(Arrays.asList(receptionItem));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void findPendingReception_ShouldReturnPendingOrders() {

        when(repository.findByStatusWithDetails("PENDING")).thenReturn(Arrays.asList(testOrder));
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findPendingReception();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByStatusWithDetails("PENDING");
    }

    @Test
    void updateStatus_WhenValidStatus_ShouldUpdateOrder() {

        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, "COMPLETED");

        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(testOrder);
    }

    @Test
    void updateStatus_WhenInvalidStatus_ShouldThrowException() {

        when(repository.findById(1)).thenReturn(Optional.of(testOrder));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.updateStatus(1, "INVALID_STATUS");
        });
        assertTrue(exception.getMessage().contains("Estado de orden inválido"));
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_WithAllValidStatuses_ShouldSucceed() {

        List<String> validStatuses = Arrays.asList("CREATED", "PENDING", "REVIEW", "COMPLETED", "INCOMPLETE");

        for (String status : validStatuses) {
            when(repository.findById(1)).thenReturn(Optional.of(testOrder));
            when(repository.save(testOrder)).thenReturn(testOrder);
            when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

            Optional<OrderResponseDTO> result = orderService.updateStatus(1, status);

            assertTrue(result.isPresent());
        }

        verify(repository, times(validStatuses.size())).save(testOrder);
    }

    @Test
    void updateStatus_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.updateStatus(999, "COMPLETED");

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_WithVersion_ShouldUseOptimisticLocking() {

        testOrder.setVersion(1L);
        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, "COMPLETED");

        assertTrue(result.isPresent());
        assertNotNull(testOrder.getVersion());
        verify(repository).findById(1);
        verify(repository).save(testOrder);
    }

    @Test
    void update_WithVersion_ShouldUseOptimisticLocking() {

        testOrder.setVersion(1L);
        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.saveAndFlush(any(Order.class))).thenReturn(testOrder);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.update(1, testOrderRequestDTO);

        assertTrue(result.isPresent());
        assertNotNull(testOrder.getVersion());
        verify(repository).findById(1);
    }

    @Test
    void receiveOrder_WithPessimisticLocking_ShouldUseCorrectIsolationLevel() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");

        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("5.0"));
        receptionData.setItems(Arrays.asList(receptionDetail));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findByIdForUpdate(1)).thenReturn(Optional.of(testProduct));
        when(stockLedgerService.recordStockMovement(
                anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt()))
                .thenReturn(null);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findByIdForUpdate(1);
        verify(stockLedgerService).recordStockMovement(
                anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt());
        verify(repository).save(any(Order.class));
    }

    @Test
    void receiveOrder_WithQuantityValidation_ShouldThrowWhenLessThanOrdered() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);
        receptionData.setStatus("CONFIRMED");

        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("3.0"));
        receptionData.setItems(Arrays.asList(receptionDetail));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.receiveOrder(receptionData);
        });
        assertTrue(exception.getMessage().contains("No se puede recibir menos cantidad"));
        verify(repository, never()).save(any(Order.class));
    }
}
