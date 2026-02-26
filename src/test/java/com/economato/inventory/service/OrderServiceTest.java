package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import com.economato.inventory.i18n.MessageKey;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.economato.inventory.dto.projection.OrderProjection;
import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.dto.request.OrderRequestDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.OrderMapper;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderDetail;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;

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
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderRequestDTO testOrderRequestDTO;
    private OrderResponseDTO testOrderResponseDTO;
    private User testUser;
    private Product testProduct;
    private UserResponseDTO testUserResponseDTO;
    private OrderProjection testProjection;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(i18nService.getMessage(ArgumentMatchers.any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        testUser.setUser("testUser");

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

        testProjection = mock(OrderProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getOrderDate()).thenReturn(LocalDateTime.now());
        lenient().when(testProjection.getStatus()).thenReturn("CREATED");

        OrderProjection.UserInfo userInfo = mock(OrderProjection.UserInfo.class);
        lenient().when(userInfo.getId()).thenReturn(1);
        lenient().when(userInfo.getName()).thenReturn("Test User");
        lenient().when(testProjection.getUsers()).thenReturn(userInfo);

        OrderProjection.OrderDetailSummary detailSummary = mock(OrderProjection.OrderDetailSummary.class);
        lenient().when(detailSummary.getQuantity()).thenReturn(new BigDecimal("5.0"));

        OrderProjection.OrderDetailSummary.ProductInfo productInfo = mock(
                OrderProjection.OrderDetailSummary.ProductInfo.class);
        lenient().when(productInfo.getId()).thenReturn(1);
        lenient().when(productInfo.getName()).thenReturn("Test Product");
        lenient().when(productInfo.getUnitPrice()).thenReturn(new BigDecimal("5.0"));
        lenient().when(detailSummary.getProduct()).thenReturn(productInfo);

        lenient().when(testProjection.getDetails()).thenReturn(Arrays.asList(detailSummary));
    }

    @Test
    void findAll_ShouldReturnPageOfOrders() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<OrderProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findAllProjectedBy(pageable)).thenReturn(page);

        Page<OrderResponseDTO> result = orderService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(repository).findAllProjectedBy(pageable);
    }

    @Test
    void findById_WhenOrderExists_ShouldReturnOrder() {

        when(repository.findProjectedById(1)).thenReturn(Optional.of(testProjection));
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testOrderResponseDTO.getId(), result.get().getId());
        verify(repository).findProjectedById(1);
    }

    @Test
    void findById_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findProjectedById(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findProjectedById(999);
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

        when(repository.findProjectedByUsersId(1)).thenReturn(Arrays.asList(testProjection));
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByUser(testUserResponseDTO);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByUsersId(1);
    }

    @Test
    void findByStatus_ShouldReturnOrdersWithStatus() {

        Page<OrderProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findProjectedByStatus(eq("CREATED"), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByStatus("CREATED");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByStatus(eq("CREATED"), any(Pageable.class));
    }

    @Test
    void findByDateRange_ShouldReturnOrdersInRange() {

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(repository.findProjectedByOrderDateBetween(start, end)).thenReturn(Arrays.asList(testProjection));
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByDateRange(start, end);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByOrderDateBetween(start, end);
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

        Page<OrderProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findProjectedByStatus(eq("PENDING"), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findPendingReception();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByStatus(eq("PENDING"), any(Pageable.class));
    }

    @Test
    void updateStatus_WhenValidStatus_ShouldUpdateOrder() {

        when(repository.findById(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, "CONFIRMED");

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
        assertTrue(exception.getMessage().contains("Estado de orden"));
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_WithAllValidStatuses_ShouldSucceed() {

        List<String> validStatuses = Arrays.asList("CREATED", "PENDING", "REVIEW", "CONFIRMED", "INCOMPLETE");

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

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, "CONFIRMED");

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
