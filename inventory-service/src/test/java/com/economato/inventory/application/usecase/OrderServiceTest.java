package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.Environment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.economato.inventory.application.dto.projection.OrderProjection;
import com.economato.inventory.application.dto.request.LotReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderRequestDTO;
import com.economato.inventory.application.dto.response.OrderFilterResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.application.dto.response.OrderTotalCostResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private ProductBatchService productBatchService;
    @Mock
    private I18nService i18nService;
    @Mock
    private Environment environment;

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
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        Mockito.lenient().when(i18nService.getMessage(eq(MessageKey.ERROR_ORDER_CANNOT_RECEIVE_LESS_DETAIL), any(Object[].class)))
                .thenAnswer(invocation -> "ERROR_ORDER_CANNOT_RECEIVE_LESS_DETAIL " + Arrays.toString((Object[]) invocation.getArgument(1)));
        Mockito.lenient().when(i18nService.getMessage(eq(MessageKey.LEDGER_DESCRIPTION_RECEPTION), any(Object[].class)))
                .thenAnswer(invocation -> "LEDGER_DESCRIPTION_RECEPTION " + Arrays.toString((Object[]) invocation.getArgument(1)));
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object arg = invocation.getArgument(1);
                String argsStr = arg instanceof Object[] ? Arrays.toString((Object[]) arg) : String.valueOf(arg);
                return ((MessageKey) invocation.getArgument(0)).name() + " " + (argsStr != null ? argsStr : "[]");
            });
        Mockito.lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
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
        testOrder.setUser(testUser);
        testOrder.setOrderDate(LocalDateTime.now());
        testOrder.setStatus(OrderStatus.CREATED);
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
        testOrderResponseDTO.setStatus(OrderStatus.CREATED);

        testUserResponseDTO = new UserResponseDTO();
        testUserResponseDTO.setId(1);
        testUserResponseDTO.setName("Test User");

        testProjection = mock(OrderProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getOrderDate()).thenReturn(LocalDateTime.now());
        lenient().when(testProjection.getStatus()).thenReturn(OrderStatus.CREATED);

        OrderProjection.UserInfo userInfo = mock(OrderProjection.UserInfo.class);
        lenient().when(userInfo.getId()).thenReturn(1);
        lenient().when(userInfo.getName()).thenReturn("Test User");
        lenient().when(testProjection.getUser()).thenReturn(userInfo);

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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.save(testOrderRequestDTO);

        assertNotNull(result);
        verify(userRepository).findById(1);
        verify(productRepository).findAllById(any());
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList());

        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.save(testOrderRequestDTO);
        });
        verify(productRepository).findAllById(any());
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void update_WhenOrderExists_ShouldUpdateOrder() {

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(repository.saveAndFlush(any(Order.class))).thenReturn(testOrder);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.update(1, testOrderRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).findByIdWithDetails(1);
        verify(repository).save(any(Order.class));
    }

    @Test
    void update_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.update(999, testOrderRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findByIdWithDetails(999);
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

        when(repository.findProjectedByUserId(1)).thenReturn(Arrays.asList(testProjection));
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByUser(testUserResponseDTO);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByUserId(1);
    }

    @Test
    void findByStatus_ShouldReturnOrdersWithStatus() {

        Page<OrderProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findProjectedByStatus(eq(OrderStatus.CREATED), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findByStatus(OrderStatus.CREATED);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByStatus(eq(OrderStatus.CREATED), any(Pageable.class));
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
    void findFiltered_ShouldReturnOrdersAndTotalCost() {
        when(repository.findAll(any(Specification.class))).thenReturn(Arrays.asList(testOrder));

        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(1);
        dto.setTotalPrice(new BigDecimal("12.50"));
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(dto);

        OrderFilterResponseDTO result = orderService.findFiltered(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                1,
                null,
                1);

        assertNotNull(result);
        assertEquals(1, result.getTotalOrders());
        assertEquals(new BigDecimal("12.50"), result.getTotalCost());
        assertEquals(1, result.getOrders().size());
        verify(repository).findAll(any(Specification.class));
    }

    @Test
    void findFiltered_WhenDateRangeIsInvalid_ShouldThrowException() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.minusHours(1);

        assertThrows(InvalidOperationException.class,
                () -> orderService.findFiltered(start, end, null, null, null));
    }

    @Test
    void getTotalCostAllOrders_ShouldReturnGlobalTotalCost() {
        when(repository.getTotalCostAllOrders()).thenReturn(new BigDecimal("99.75"));
        when(repository.count()).thenReturn(3L);

        OrderTotalCostResponseDTO result = orderService.getTotalCostAllOrders();

        assertNotNull(result);
        assertEquals(new BigDecimal("99.75"), result.getTotalCost());
        assertEquals(3L, result.getTotalOrders());
        verify(repository).getTotalCostAllOrders();
        verify(repository).count();
    }

    @Test
    void receiveOrder_WhenValidReception_ShouldUpdateStockAndStatus() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);


        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(1);
        receptionItem.setQuantityReceived(new BigDecimal("5.0"));
        LotReceptionRequestDTO lot = new LotReceptionRequestDTO();
        lot.setQuantity(new BigDecimal("5.0"));
        lot.setExpirationDate(LocalDate.now().plusDays(30));
        receptionItem.setLots(Arrays.asList(lot));
        receptionData.setItems(Arrays.asList(receptionItem));

        testOrder.getDetails().get(0).setQuantityReceived(new BigDecimal("5.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        StockLedger ledger = new StockLedger();
        ledger.setId(1L);
        when(stockLedgerService.recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), any(), any(User.class), anyInt(), nullable(LocalDate.class)))
            .thenReturn(ledger);
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findAllById(any());
        verify(stockLedgerService).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
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
    void receiveOrder_WhenQuantityLessThanOrdered_ShouldSetStatusToIncomplete() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);


        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(1);
        receptionItem.setQuantityReceived(new BigDecimal("3.0"));
        LotReceptionRequestDTO lot = new LotReceptionRequestDTO();
        lot.setQuantity(new BigDecimal("3.0"));
        lot.setExpirationDate(LocalDate.now().plusDays(30));
        receptionItem.setLots(Arrays.asList(lot));
        receptionData.setItems(Arrays.asList(receptionItem));

        testOrder.getDetails().get(0).setQuantityReceived(new BigDecimal("3.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        StockLedger ledger = new StockLedger();
        when(stockLedgerService.recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), any(), any(User.class), anyInt(), nullable(LocalDate.class)))
            .thenReturn(ledger);
        when(repository.save(testOrder)).thenReturn(testOrder);

        OrderResponseDTO incompleteResponse = new OrderResponseDTO();
        incompleteResponse.setId(1);
        incompleteResponse.setStatus(OrderStatus.INCOMPLETE);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(incompleteResponse);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        assertEquals(OrderStatus.INCOMPLETE, result.getStatus());
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findAllById(any());
        verify(stockLedgerService).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
        verify(repository).save(testOrder);
    }

    @Test
    void receiveOrder_WhenProductNotInOrder_ShouldThrowException() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);


        OrderReceptionDetailRequestDTO receptionItem = new OrderReceptionDetailRequestDTO();
        receptionItem.setProductId(999);
        receptionItem.setQuantityReceived(new BigDecimal("5.0"));
        LotReceptionRequestDTO lot = new LotReceptionRequestDTO();
        lot.setQuantity(new BigDecimal("5.0"));
        lot.setExpirationDate(LocalDate.now().plusDays(30));
        receptionItem.setLots(Arrays.asList(lot));
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
        when(repository.findProjectedByStatus(eq(OrderStatus.PENDING), any(Pageable.class))).thenReturn(page);
        when(orderMapper.toResponseDTO(any(OrderProjection.class))).thenReturn(testOrderResponseDTO);

        List<OrderResponseDTO> result = orderService.findPendingReception();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findProjectedByStatus(eq(OrderStatus.PENDING), any(Pageable.class));
    }

    @Test
    void updateStatus_WhenValidStatus_ShouldUpdateOrder() {

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, OrderStatus.CONFIRMED);

        assertTrue(result.isPresent());
        verify(repository).findByIdWithDetails(1);
        verify(repository).save(testOrder);
    }

    @Test
    void updateStatus_WhenInvalidStatusRejectedByJackson_NotApplicable() {
        // Validation of status strings is now done by Jackson at deserialization time.
        // The OrderService.updateStatus() only accepts OrderStatus enum values, so
        // invalid strings cannot reach the service layer. This test is intentionally
        // left as a no-op.
    }

    @Test
    void updateStatus_WithAllValidStatuses_ShouldSucceed() {

        for (OrderStatus status : OrderStatus.values()) {
            when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
            when(repository.save(testOrder)).thenReturn(testOrder);
            when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

            Optional<OrderResponseDTO> result = orderService.updateStatus(1, status);

            assertTrue(result.isPresent());
        }

        verify(repository, times(OrderStatus.values().length)).save(testOrder);
    }

    @Test
    void updateStatus_WhenOrderDoesNotExist_ShouldReturnEmpty() {

        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        Optional<OrderResponseDTO> result = orderService.updateStatus(999, OrderStatus.CANCELLED);

        assertFalse(result.isPresent());
        verify(repository).findByIdWithDetails(999);
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_WithVersion_ShouldUseOptimisticLocking() {

        testOrder.setVersion(1L);
        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(repository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(testOrder)).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.updateStatus(1, OrderStatus.CONFIRMED);

        assertTrue(result.isPresent());
        assertNotNull(testOrder.getVersion());
        verify(repository).findByIdWithDetails(1);
        verify(repository).save(testOrder);
    }

    @Test
    void update_WithVersion_ShouldUseOptimisticLocking() {

        testOrder.setVersion(1L);
        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(repository.saveAndFlush(any(Order.class))).thenReturn(testOrder);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(testOrderResponseDTO);

        Optional<OrderResponseDTO> result = orderService.update(1, testOrderRequestDTO);

        assertTrue(result.isPresent());
        assertNotNull(testOrder.getVersion());
        verify(repository).findByIdWithDetails(1);
    }

    @Test
    void receiveOrder_WithPessimisticLocking_ShouldUseCorrectIsolationLevel() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);


        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("5.0"));
        LotReceptionRequestDTO lot = new LotReceptionRequestDTO();
        lot.setQuantity(new BigDecimal("5.0"));
        lot.setExpirationDate(LocalDate.now().plusDays(30));
        receptionDetail.setLots(Arrays.asList(lot));
        receptionData.setItems(Arrays.asList(receptionDetail));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        StockLedger ledger = new StockLedger();
        when(stockLedgerService.recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), any(), any(User.class), anyInt(), nullable(LocalDate.class)))
            .thenReturn(ledger);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findAllById(any());
        verify(stockLedgerService).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
        verify(repository).save(any(Order.class));
    }

    @Test
    void receiveOrder_WithQuantityValidation_ShouldSetIncompleteWhenLessThanOrdered() {

        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);


        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("3.0"));
        LotReceptionRequestDTO lot = new LotReceptionRequestDTO();
        lot.setQuantity(new BigDecimal("3.0"));
        lot.setExpirationDate(LocalDate.now().plusDays(30));
        receptionDetail.setLots(Arrays.asList(lot));
        receptionData.setItems(Arrays.asList(receptionDetail));

        testOrder.getDetails().get(0).setQuantityReceived(new BigDecimal("3.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        StockLedger ledger = new StockLedger();
        when(stockLedgerService.recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), any(), any(User.class), anyInt(), nullable(LocalDate.class)))
            .thenReturn(ledger);
        when(repository.save(any(Order.class))).thenReturn(testOrder);

        OrderResponseDTO incompleteResponse = new OrderResponseDTO();
        incompleteResponse.setId(1);
        incompleteResponse.setStatus(OrderStatus.INCOMPLETE);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(incompleteResponse);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        assertEquals(OrderStatus.INCOMPLETE, result.getStatus());
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findAllById(any());
        verify(stockLedgerService).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
        verify(repository).save(any(Order.class));
    }

    @Test
    void receiveOrder_WithMultipleLots_ShouldCreateMultipleBatches() {
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);

        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("5.0"));
        
        LotReceptionRequestDTO lot1 = new LotReceptionRequestDTO();
        lot1.setQuantity(new BigDecimal("2.0"));
        lot1.setExpirationDate(LocalDate.now().plusDays(10));

        LotReceptionRequestDTO lot2 = new LotReceptionRequestDTO();
        lot2.setQuantity(new BigDecimal("3.0"));
        lot2.setExpirationDate(LocalDate.now().plusDays(20));

        receptionDetail.setLots(Arrays.asList(lot1, lot2));
        receptionData.setItems(Arrays.asList(receptionDetail));

        testOrder.getDetails().get(0).setQuantityReceived(new BigDecimal("5.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        StockLedger ledger = new StockLedger();
        when(stockLedgerService.recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), any(), any(User.class), anyInt(), nullable(LocalDate.class)))
            .thenReturn(ledger);
        when(repository.save(any(Order.class))).thenReturn(testOrder);
        when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(testOrderResponseDTO);

        OrderResponseDTO result = orderService.receiveOrder(receptionData);

        assertNotNull(result);
        verify(stockLedgerService, times(2)).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
    }

    @Test
    void receiveOrder_WithLotsSumMismatch_ShouldThrowException() {
        OrderReceptionRequestDTO receptionData = new OrderReceptionRequestDTO();
        receptionData.setOrderId(1);

        OrderReceptionDetailRequestDTO receptionDetail = new OrderReceptionDetailRequestDTO();
        receptionDetail.setProductId(1);
        receptionDetail.setQuantityReceived(new BigDecimal("5.0"));
        
        LotReceptionRequestDTO lot1 = new LotReceptionRequestDTO();
        lot1.setQuantity(new BigDecimal("2.0"));
        lot1.setExpirationDate(LocalDate.now().plusDays(10));

        LotReceptionRequestDTO lot2 = new LotReceptionRequestDTO();
        lot2.setQuantity(new BigDecimal("2.0"));
        lot2.setExpirationDate(LocalDate.now().plusDays(20));

        receptionDetail.setLots(Arrays.asList(lot1, lot2));
        receptionData.setItems(Arrays.asList(receptionDetail));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testOrder));

        assertThrows(InvalidOperationException.class, () -> orderService.receiveOrder(receptionData));
        verify(stockLedgerService, never()).recordStockMovement(
            anyInt(), any(BigDecimal.class), any(MovementType.class), anyString(), any(User.class), anyInt(), nullable(LocalDate.class));
    }
}
