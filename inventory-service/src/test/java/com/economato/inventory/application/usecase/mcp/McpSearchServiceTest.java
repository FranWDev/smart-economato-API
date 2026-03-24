package com.economato.inventory.application.usecase.mcp;

import com.economato.inventory.application.dto.mcp.McpSearchResultDto;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpSearchServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private McpSearchService mcpSearchService;

    private Product testProduct;
    private Recipe testRecipe;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Tomato");
        testProduct.setProductCode("TOM001");
        testProduct.setCurrentStock(new BigDecimal("20.0"));
        testProduct.setUnitPrice(new BigDecimal("0.5"));
        testProduct.setUnit("KG");

        testRecipe = new Recipe();
        testRecipe.setId(1);
        testRecipe.setName("Tomato Soup");
        testRecipe.setTotalCost(new BigDecimal("5.0"));
        testRecipe.setPresentation("Bright red");
        testRecipe.setElaboration("Boil tomatoes");
        testRecipe.setAllergens(new HashSet<>());
    }

    @Test
    void unifiedSearch_ShouldReturnMatchesFromBothEntities() {
        when(productRepository.findAll()).thenReturn(Arrays.asList(testProduct));
        when(recipeRepository.findAllWithAllergens()).thenReturn(Arrays.asList(testRecipe));

        McpSearchResultDto result = mcpSearchService.unifiedSearch("Tomato");

        assertNotNull(result);
        assertEquals(1, result.getProducts().size());
        assertEquals(1, result.getRecipes().size());
        assertEquals("Tomato", result.getProducts().get(0).getName());
        assertEquals("Tomato Soup", result.getRecipes().get(0).getName());
    }

    @Test
    void unifiedSearch_WhenNoMatches_ShouldReturnEmptyLists() {
        when(productRepository.findAll()).thenReturn(Arrays.asList(testProduct));
        when(recipeRepository.findAllWithAllergens()).thenReturn(Arrays.asList(testRecipe));

        McpSearchResultDto result = mcpSearchService.unifiedSearch("Cucumber");

        assertTrue(result.getProducts().isEmpty());
        assertTrue(result.getRecipes().isEmpty());
    }

    @Test
    void unifiedSearch_WithNullQuery_ShouldReturnEmptyLists() {
        McpSearchResultDto result = mcpSearchService.unifiedSearch(null);
        assertTrue(result.getProducts().isEmpty());
        assertTrue(result.getRecipes().isEmpty());
    }
}
