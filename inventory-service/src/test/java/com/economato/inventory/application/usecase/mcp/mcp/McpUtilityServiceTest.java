package com.economato.inventory.application.usecase.mcp.mcp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.economato.inventory.application.dto.mcp.mcp.McpBulkRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpSystemContextDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpUtilityServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private McpUtilityService mcpUtilityService;

    private Product testProduct;
    private Order testOrder;
    private Recipe testRecipe;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Milk");
        testProduct.setProductCode("MLK001");
        testProduct.setCurrentStock(new BigDecimal("10.0"));
        testProduct.setUnitPrice(new BigDecimal("1.5"));
        testProduct.setUnit("L");

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setDetails(new ArrayList<>());
        testOrder.setOrderDate(LocalDateTime.now());

        testRecipe = new Recipe();
        testRecipe.setId(1);
        testRecipe.setName("Pancakes");
        testRecipe.setTotalCost(new BigDecimal("2.5"));
        testRecipe.setElaboration("Mix and fry");
        testRecipe.setPresentation("Stack them up");
        testRecipe.setAllergens(new HashSet<>());
    }

    @Test
    void getSystemContext_ShouldReturnAggregatedStats() {
        when(productRepository.count()).thenReturn(10L);
        when(orderRepository.findByStatusInWithDetails(any())).thenReturn(Arrays.asList(testOrder));
        when(recipeRepository.count()).thenReturn(5L);

        McpSystemContextDto result = mcpUtilityService.getSystemContext();

        assertNotNull(result);
        assertEquals(10, result.getTotalProducts());
        assertEquals(1, result.getPendingOrdersCount());
        assertEquals(5, result.getTotalRecipes());
    }

    @Test
    void getProductsWithFilters_ShouldApplyFiltersCorrectly() {
        when(productRepository.findAll()).thenReturn(Arrays.asList(testProduct));

        List<McpProductDto> result = mcpUtilityService.getProductsWithFilters(BigDecimal.ONE, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Milk", result.get(0).getName());
    }


    @Test
    void getProductsBulk_ByIds_ShouldReturnCorrectProducts() {
        when(productRepository.findAllById(anyList())).thenReturn(Arrays.asList(testProduct));
        McpBulkRequest request = new McpBulkRequest(Arrays.asList(1), null);

        List<McpProductDto> result = mcpUtilityService.getProductsBulk(request);

        assertEquals(1, result.size());
        assertEquals("Milk", result.get(0).getName());
    }
}
