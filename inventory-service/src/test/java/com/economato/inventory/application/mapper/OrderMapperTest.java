package com.economato.inventory.application.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.economato.inventory.application.dto.projection.OrderProjection;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.domain.model.OrderStatus;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTest {

    private OrderMapper orderMapper;

    private OrderProjection orderProjection;

    @BeforeEach
    void setUp() {
        orderMapper = Mappers.getMapper(OrderMapper.class);
        ReflectionTestUtils.setField(orderMapper, "orderDetailMapper", Mappers.getMapper(OrderDetailMapper.class));

        // Create OrderProjection implementation using static inner classes
        TestOrderProjection projection = new TestOrderProjection();
        projection.setId(1);
        projection.setUser(new TestUserInfo(1, "Test User"));
        projection.setOrderDate(LocalDateTime.now());
        projection.setStatus(OrderStatus.PENDING);
        projection.setDetails(Arrays.asList(
                new TestOrderDetailSummary(new BigDecimal("2"), new BigDecimal("10.00")),
                new TestOrderDetailSummary(new BigDecimal("3"), new BigDecimal("5.50"))));
        
        orderProjection = projection;
    }

    // Static inner classes for projection mocks to avoid NoClassDefFound with anonymous classes
    private static class TestOrderProjection implements OrderProjection {
        private Integer id;
        private UserInfo user;
        private SupplierInfo supplier;
        private LocalDateTime orderDate;
        private OrderStatus status;
        private List<OrderDetailSummary> details;

        @Override public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        @Override public UserInfo getUser() { return user; }
        public void setUser(UserInfo user) { this.user = user; }

        @Override public SupplierInfo getSupplier() { return supplier; }
        public void setSupplier(SupplierInfo supplier) { this.supplier = supplier; }

        @Override public LocalDateTime getOrderDate() { return orderDate; }
        public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

        @Override public OrderStatus getStatus() { return status; }
        public void setStatus(OrderStatus status) { this.status = status; }

        @Override public List<OrderDetailSummary> getDetails() { return details; }
        public void setDetails(List<OrderDetailSummary> details) { this.details = details; }
    }

    private static class TestUserInfo implements OrderProjection.UserInfo {
        private final Integer id;
        private final String name;
        public TestUserInfo(Integer id, String name) { this.id = id; this.name = name; }
        @Override public Integer getId() { return id; }
        @Override public String getName() { return name; }
    }

    private static class TestOrderDetailSummary implements OrderProjection.OrderDetailSummary {
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        public TestOrderDetailSummary(BigDecimal quantity, BigDecimal unitPrice) { this.quantity = quantity; this.unitPrice = unitPrice; }
        @Override public BigDecimal getQuantity() { return quantity; }
        @Override public BigDecimal getQuantityReceived() { return BigDecimal.ZERO; }
        @Override public OrderProjection.OrderDetailSummary.ProductInfo getProduct() {
            return new OrderProjection.OrderDetailSummary.ProductInfo() {
                @Override public Integer getId() { return 1; }
                @Override public String getName() { return "Test Product"; }
                @Override public BigDecimal getUnitPrice() { return unitPrice; }
            };
        }
    }

    @Test
    void testToResponseDTOFromProjectionCalculatesTotalPrice() {
        // When
        OrderResponseDTO result = orderMapper.toResponseDTO(orderProjection);

        // Then
        assertNotNull(result);
        assertNotNull(result.getTotalPrice());

        // Expected: (2 * 10.00) + (3 * 5.50) = 20.00 + 16.50 = 36.50
        assertEquals(new BigDecimal("36.50"), result.getTotalPrice());
    }

    @Test
    void testToResponseDTOFromProjectionWithEmptyDetails() {
        // Given an empty projection
        TestOrderProjection emptyProjection = new TestOrderProjection();
        emptyProjection.setId(1);
        emptyProjection.setUser(new TestUserInfo(1, "Test User"));
        emptyProjection.setOrderDate(LocalDateTime.now());
        emptyProjection.setStatus(OrderStatus.PENDING);
        emptyProjection.setDetails(Arrays.asList()); // Empty list

        // When
        OrderResponseDTO result = orderMapper.toResponseDTO(emptyProjection);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
    }

    @Test
    void testToResponseDTOFromProjectionWithNullDetails() {
        // Given a projection with null details
        TestOrderProjection nullDetailsProjection = new TestOrderProjection();
        nullDetailsProjection.setId(1);
        nullDetailsProjection.setUser(new TestUserInfo(1, "Test User"));
        nullDetailsProjection.setOrderDate(LocalDateTime.now());
        nullDetailsProjection.setStatus(OrderStatus.PENDING);
        nullDetailsProjection.setDetails(null); // Null list

        // When
        OrderResponseDTO result = orderMapper.toResponseDTO(nullDetailsProjection);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
    }
}
