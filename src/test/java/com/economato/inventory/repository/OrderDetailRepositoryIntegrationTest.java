package com.economato.inventory.repository;

import com.economato.inventory.dto.projection.PendingProductQuantity;
import com.economato.inventory.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("OrderDetailRepository Integration Tests")
class OrderDetailRepositoryIntegrationTest {

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Product product1;
    private Product product2;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Crear usuario de prueba
        testUser = new User();
        testUser.setUser("testuser");
        testUser.setPassword("hashedPassword");
        testUser.setName("Test User");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);

        // Crear productos de prueba
        product1 = new Product();
        product1.setName("Producto 1");
        product1.setProductCode("PROD001");
        product1.setType("kg");
        product1.setUnit("kg");
        product1.setCurrentStock(BigDecimal.TEN);
        product1.setUnitPrice(BigDecimal.valueOf(10.0));
        product1.setMinimumStock(BigDecimal.ONE);
        product1 = productRepository.save(product1);

        product2 = new Product();
        product2.setName("Producto 2");
        product2.setProductCode("PROD002");
        product2.setType("l");
        product2.setUnit("l");
        product2.setCurrentStock(BigDecimal.ZERO);
        product2.setUnitPrice(BigDecimal.valueOf(5.0));
        product2.setMinimumStock(BigDecimal.ONE);
        product2 = productRepository.save(product2);
    }

    @Test
    @DisplayName("findPendingQuantityPerProduct devuelve cantidades de órdenes activas")
    void testFindPendingQuantityPerProduct_WithActiveOrders() {
        // --- Setup ---
        // Crear orden en estado CREATED
        Order createdOrder = new Order();
        createdOrder.setUser(testUser);
        createdOrder.setOrderDate(LocalDateTime.now());
        createdOrder.setStatus(OrderStatus.CREATED);
        createdOrder = orderRepository.save(createdOrder);

        // Agregar detalles a la orden
        OrderDetail detail1 = new OrderDetail();
        detail1.setOrder(createdOrder);
        detail1.setProduct(product1);
        detail1.setQuantity(BigDecimal.valueOf(5));
        detail1.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail1);

        OrderDetail detail2 = new OrderDetail();
        detail2.setOrder(createdOrder);
        detail2.setProduct(product2);
        detail2.setQuantity(BigDecimal.valueOf(3));
        detail2.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail2);

        // --- Execute ---
        List<PendingProductQuantity> results = orderDetailRepository.findPendingQuantityPerProduct();

        // --- Verify ---
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);

        PendingProductQuantity result1 = results.stream()
                .filter(r -> r.getProductId().equals(product1.getId()))
                .findFirst()
                .orElseThrow();

        PendingProductQuantity result2 = results.stream()
                .filter(r -> r.getProductId().equals(product2.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(result1.getPendingQuantity().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(5).stripTrailingZeros());
        assertThat(result2.getPendingQuantity().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(3).stripTrailingZeros());
    }

    @Test
    @DisplayName("findPendingQuantityPerProduct excluye órdenes confirmadas")
    void testFindPendingQuantityPerProduct_ExcludesConfirmedOrders() {
        // --- Setup ---
        // Crear orden en estado CONFIRMED (debe ser excluida)
        Order confirmedOrder = new Order();
        confirmedOrder.setUser(testUser);
        confirmedOrder.setOrderDate(LocalDateTime.now());
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);
        confirmedOrder = orderRepository.save(confirmedOrder);

        OrderDetail detail = new OrderDetail();
        detail.setOrder(confirmedOrder);
        detail.setProduct(product1);
        detail.setQuantity(BigDecimal.valueOf(10));
        detail.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail);

        // --- Execute ---
        List<PendingProductQuantity> results = orderDetailRepository.findPendingQuantityPerProduct();

        // --- Verify ---
        // La orden CONFIRMED no debe aparecer en los pendientes
        results.forEach(r -> {
            if (r.getProductId().equals(product1.getId())) {
                assertThat(r.getPendingQuantity()).as("Órdenes CONFIRMED no deben incluirse")
                        .isNotEqualTo(BigDecimal.valueOf(10));
            }
        });
    }

    @Test
    @DisplayName("findPendingQuantityPerProduct incluye múltiples estados activos")
    void testFindPendingQuantityPerProduct_WithMultipleActiveStates() {
        // --- Setup ---
        // Orden con estado CREATED
        Order createdOrder = new Order();
        createdOrder.setUser(testUser);
        createdOrder.setOrderDate(LocalDateTime.now());
        createdOrder.setStatus(OrderStatus.CREATED);
        createdOrder = orderRepository.save(createdOrder);

        // Orden con estado PENDING
        Order pendingOrder = new Order();
        pendingOrder.setUser(testUser);
        pendingOrder.setOrderDate(LocalDateTime.now());
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder = orderRepository.save(pendingOrder);

        // Orden con estado REVIEW
        Order reviewOrder = new Order();
        reviewOrder.setUser(testUser);
        reviewOrder.setOrderDate(LocalDateTime.now());
        reviewOrder.setStatus(OrderStatus.REVIEW);
        reviewOrder = orderRepository.save(reviewOrder);

        // Agregar detalle al producto en cada orden
        OrderDetail detail1 = new OrderDetail();
        detail1.setOrder(createdOrder);
        detail1.setProduct(product1);
        detail1.setQuantity(BigDecimal.valueOf(2));
        detail1.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail1);

        OrderDetail detail2 = new OrderDetail();
        detail2.setOrder(pendingOrder);
        detail2.setProduct(product1);
        detail2.setQuantity(BigDecimal.valueOf(3));
        detail2.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail2);

        OrderDetail detail3 = new OrderDetail();
        detail3.setOrder(reviewOrder);
        detail3.setProduct(product1);
        detail3.setQuantity(BigDecimal.valueOf(5));
        detail3.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail3);

        // --- Execute ---
        List<PendingProductQuantity> results = orderDetailRepository.findPendingQuantityPerProduct();

        // --- Verify ---
        PendingProductQuantity result = results.stream()
                .filter(r -> r.getProductId().equals(product1.getId()))
                .findFirst()
                .orElseThrow();

        // Debe sumar: 2 + 3 + 5 = 10
        assertThat(result.getPendingQuantity().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(10).stripTrailingZeros());
    }

    @Test
    @DisplayName("findPendingQuantityPerProduct devuelve lista vacía sin órdenes activas")
    void testFindPendingQuantityPerProduct_EmptyWhenNoActiveOrders() {
        // --- Setup ---
        // Crear solo órdenes en estados que pueden ser excluidos
        Order cancelledOrder = new Order();
        cancelledOrder.setUser(testUser);
        cancelledOrder.setOrderDate(LocalDateTime.now());
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder = orderRepository.save(cancelledOrder);

        OrderDetail detail = new OrderDetail();
        detail.setOrder(cancelledOrder);
        detail.setProduct(product1);
        detail.setQuantity(BigDecimal.valueOf(5));
        detail.setQuantityReceived(BigDecimal.ZERO);
        orderDetailRepository.save(detail);

        // --- Execute ---
        List<PendingProductQuantity> results = orderDetailRepository.findPendingQuantityPerProduct();

        // --- Verify ---
        // No debe haber cantidades pendientes de órdenes CANCELLED
        boolean hasProduct1Pending = results.stream()
                .anyMatch(r -> r.getProductId().equals(product1.getId()) &&
                        r.getPendingQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertThat(hasProduct1Pending).isFalse();
    }
}
