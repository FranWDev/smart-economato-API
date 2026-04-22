package com.economato.inventory.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.economato.inventory.application.dto.projection.RecipeProjection;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;

class RecipeMapperTest {

    private RecipeMapper mapper;
    private RecipeComponentMapper recipeComponentMapper;
    private AllergenMapper allergenMapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RecipeMapper.class);
        recipeComponentMapper = mock(RecipeComponentMapper.class);
        allergenMapper = mock(AllergenMapper.class);
        
        ReflectionTestUtils.setField(mapper, "recipeComponentMapper", recipeComponentMapper);
        ReflectionTestUtils.setField(mapper, "allergenMapper", allergenMapper);
    }

    @Test
    void toResponseDTO_FromRecipe_ShouldCalculateTotalCost() {
        Recipe recipe = new Recipe();
        recipe.setComponents(new HashSet<>());

        Product product = new Product();
        product.setUnitPrice(new BigDecimal("10.00"));
        product.setAvailabilityPercentage(new BigDecimal("50.00"));

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);
        component.setQuantity(new BigDecimal("2.0"));
        recipe.addComponent(component);

        // Cost = (2.0 * 100 / 50) * 10.00 = 4.0 * 10.00 = 40.00
        RecipeResponseDTO result = mapper.toResponseDTO(recipe);

        assertEquals(new BigDecimal("40.00"), result.getTotalCost());
    }

    @Test
    void toResponseDTO_FromProjection_ShouldRecalculateTotalCost() {
        RecipeProjection projection = mock(RecipeProjection.class);
        when(projection.getTotalCost()).thenReturn(new BigDecimal("999.99")); // Mocked persisted value

        RecipeProjection.RecipeComponentSummary component = mock(RecipeProjection.RecipeComponentSummary.class);
        RecipeProjection.RecipeComponentSummary.ProductInfo product = mock(RecipeProjection.RecipeComponentSummary.ProductInfo.class);

        when(component.getQuantity()).thenReturn(new BigDecimal("2.0"));
        when(component.getProduct()).thenReturn(product);
        when(product.getUnitPrice()).thenReturn(new BigDecimal("10.00"));
        when(product.getAvailabilityPercentage()).thenReturn(new BigDecimal("50.00"));

        when(projection.getComponents()).thenReturn(List.of(component));

        // Cost should be recalculated to 40.00 regardless of the 999.99 persisted value
        RecipeResponseDTO result = mapper.toResponseDTO(projection);

        assertEquals(new BigDecimal("40.00"), result.getTotalCost());
    }
    
    @Test
    void calculateTotalCost_WithHighPrecision_ShouldMatchService() {
        // Test precision consistency (10 decimal places intermediate)
        // 1 / 3 = 0.3333333333...
        // Component: Qty=1, Price=1, Pct=300 (just to test division)
        // Cost = (1 * 100 / 300) * 1.00 = 0.3333333333...
        // Rounds to 0.33 at the end.
        
        Recipe recipe = new Recipe();
        recipe.setComponents(new HashSet<>());

        Product product = new Product();
        product.setUnitPrice(new BigDecimal("1.00"));
        product.setAvailabilityPercentage(new BigDecimal("300.00"));

        RecipeComponent component = new RecipeComponent();
        component.setProduct(product);
        component.setQuantity(new BigDecimal("1.0"));
        recipe.addComponent(component);

        RecipeResponseDTO result = mapper.toResponseDTO(recipe);

        assertEquals(new BigDecimal("0.33"), result.getTotalCost());
    }
}
