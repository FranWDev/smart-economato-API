package com.economato.inventory.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.economato.inventory.dto.projection.OrderProjection;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.model.OrderStatus;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTest {

    private OrderMapper orderMapper;

    private OrderProjection orderProjection;

    @BeforeEach
    void setUp() {
        orderMapper = Mappers.getMapper(OrderMapper.class);
        ReflectionTestUtils.setField(orderMapper, "orderDetailMapper", Mappers.getMapper(OrderDetailMapper.class));

        // Mock OrderProjection with details
        orderProjection = new OrderProjection() {
            @Override
            public Integer getId() {
                return 1;
            }

            @Override
            public UserInfo getUser() {
                return new UserInfo() {
                    @Override
                    public Integer getId() {
                        return 1;
                    }

                    @Override
                    public String getName() {
                        return "Test User";
                    }
                };
            }

            @Override
            public LocalDateTime getOrderDate() {
                return LocalDateTime.now();
            }

            @Override
            public OrderStatus getStatus() {
                return OrderStatus.PENDING;
            }

            @Override
            public List<OrderDetailSummary> getDetails() {
                return Arrays.asList(
                        createDetailSummary(new BigDecimal("2"), new BigDecimal("10.00")),
                        createDetailSummary(new BigDecimal("3"), new BigDecimal("5.50")));
            }

            private OrderDetailSummary createDetailSummary(BigDecimal quantity, BigDecimal unitPrice) {
                return new OrderDetailSummary() {
                    @Override
                    public BigDecimal getQuantity() {
                        return quantity;
                    }

                    @Override
                    public BigDecimal getQuantityReceived() {
                        return BigDecimal.ZERO;
                    }

                    @Override
                    public ProductInfo getProduct() {
                        return new ProductInfo() {
                            @Override
                            public Integer getId() {
                                return 1;
                            }

                            @Override
                            public String getName() {
                                return "Test Product";
                            }

                            @Override
                            public BigDecimal getUnitPrice() {
                                return unitPrice;
                            }
                        };
                    }
                };
            }
        };
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
        OrderProjection emptyProjection = new OrderProjection() {
            @Override
            public Integer getId() {
                return 1;
            }

            @Override
            public UserInfo getUser() {
                return new UserInfo() {
                    @Override
                    public Integer getId() {
                        return 1;
                    }

                    @Override
                    public String getName() {
                        return "Test User";
                    }
                };
            }

            @Override
            public LocalDateTime getOrderDate() {
                return LocalDateTime.now();
            }

            @Override
            public OrderStatus getStatus() {
                return OrderStatus.PENDING;
            }

            @Override
            public List<OrderDetailSummary> getDetails() {
                return Arrays.asList(); // Empty list
            }
        };

        // When
        OrderResponseDTO result = orderMapper.toResponseDTO(emptyProjection);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
    }

    @Test
    void testToResponseDTOFromProjectionWithNullDetails() {
        // Given a projection with null details
        OrderProjection nullDetailsProjection = new OrderProjection() {
            @Override
            public Integer getId() {
                return 1;
            }

            @Override
            public UserInfo getUser() {
                return new UserInfo() {
                    @Override
                    public Integer getId() {
                        return 1;
                    }

                    @Override
                    public String getName() {
                        return "Test User";
                    }
                };
            }

            @Override
            public LocalDateTime getOrderDate() {
                return LocalDateTime.now();
            }

            @Override
            public OrderStatus getStatus() {
                return OrderStatus.PENDING;
            }

            @Override
            public List<OrderDetailSummary> getDetails() {
                return null; // Null list
            }
        };

        // When
        OrderResponseDTO result = orderMapper.toResponseDTO(nullDetailsProjection);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalPrice());
    }
}
