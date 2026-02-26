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

import com.economato.inventory.dto.projection.RecipeProjection;
import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.dto.response.RecipeStatsResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.RecipeMapper;
import com.economato.inventory.mapper.StatsMapper;
import com.economato.inventory.model.Allergen;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeComponent;
import com.economato.inventory.repository.AllergenRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository repository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AllergenRepository allergenRepository;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private StatsMapper statsMapper;

    @Mock
    private StockLedgerService stockLedgerService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private RecipeService recipeService;

    private Recipe testRecipe;
    private RecipeRequestDTO testRecipeRequestDTO;
    private RecipeResponseDTO testRecipeResponseDTO;
    private Product testProduct;
    private Allergen testAllergen;
    private RecipeProjection testProjection;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(i18nService.getMessage(ArgumentMatchers.any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setUnitPrice(new BigDecimal("5.00"));

        testAllergen = new Allergen();
        testAllergen.setId(1);
        testAllergen.setName("Test Allergen");

        testRecipe = new Recipe();
        testRecipe.setId(1);
        testRecipe.setName("Test Recipe");
        testRecipe.setElaboration("Test elaboration");
        testRecipe.setPresentation("Test presentation");
        testRecipe.setComponents(new ArrayList<>());
        testRecipe.setAllergens(new HashSet<>());
        testRecipe.setTotalCost(new BigDecimal("10.00"));

        RecipeComponent component = new RecipeComponent();
        component.setProduct(testProduct);
        component.setQuantity(new BigDecimal("2.0"));
        testRecipe.addComponent(component);

        testRecipeRequestDTO = new RecipeRequestDTO();
        testRecipeRequestDTO.setName("Test Recipe");
        testRecipeRequestDTO.setElaboration("Test elaboration");
        testRecipeRequestDTO.setPresentation("Test presentation");

        RecipeComponentRequestDTO componentDTO = new RecipeComponentRequestDTO();
        componentDTO.setProductId(1);
        componentDTO.setQuantity(new BigDecimal("2.0"));
        testRecipeRequestDTO.setComponents(Arrays.asList(componentDTO));
        testRecipeRequestDTO.setAllergenIds(Arrays.asList(1));

        testRecipeResponseDTO = new RecipeResponseDTO();
        testRecipeResponseDTO.setId(1);
        testRecipeResponseDTO.setName("Test Recipe");
        testRecipeResponseDTO.setTotalCost(new BigDecimal("10.00"));

        testProjection = mock(RecipeProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getName()).thenReturn("Test Recipe");
        lenient().when(testProjection.getTotalCost()).thenReturn(new BigDecimal("10.00"));
        lenient().when(testProjection.getElaboration()).thenReturn("Test elaboration");
        lenient().when(testProjection.getPresentation()).thenReturn("Test presentation");

        RecipeProjection.RecipeComponentSummary compSummary = mock(RecipeProjection.RecipeComponentSummary.class);
        lenient().when(compSummary.getId()).thenReturn(1);
        lenient().when(compSummary.getQuantity()).thenReturn(new BigDecimal("2.0"));

        RecipeProjection.RecipeComponentSummary.ProductInfo prodInfo = mock(
                RecipeProjection.RecipeComponentSummary.ProductInfo.class);
        lenient().when(prodInfo.getId()).thenReturn(1);
        lenient().when(prodInfo.getName()).thenReturn("Test Product");
        lenient().when(prodInfo.getUnitPrice()).thenReturn(new BigDecimal("5.00"));
        lenient().when(compSummary.getProduct()).thenReturn(prodInfo);

        lenient().when(testProjection.getComponents()).thenReturn(Arrays.asList(compSummary));

        RecipeProjection.AllergenInfo allergenInfo = mock(RecipeProjection.AllergenInfo.class);
        lenient().when(allergenInfo.getId()).thenReturn(1);
        lenient().when(allergenInfo.getName()).thenReturn("Test Allergen");

        lenient().when(testProjection.getAllergens()).thenReturn(Collections.singleton(allergenInfo));

        // Mocking mapper behavior to return dummy entity to enable logic flow
        lenient().when(recipeMapper.toEntity(any(RecipeRequestDTO.class))).thenReturn(testRecipe);
    }

    @Test
    void findAll_ShouldReturnPageOfRecipes() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<RecipeProjection> page = new PageImpl<>(Arrays.asList(testProjection));
        when(repository.findByIsHiddenFalse(any(Pageable.class))).thenReturn(page);
        when(recipeMapper.toResponseDTO(any(RecipeProjection.class))).thenReturn(testRecipeResponseDTO);

        Page<RecipeResponseDTO> result = recipeService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(repository).findByIsHiddenFalse(any(Pageable.class));
    }

    @Test
    void findById_WhenRecipeExists_ShouldReturnRecipe() {

        when(repository.findProjectedById(1)).thenReturn(Optional.of(testProjection));
        when(recipeMapper.toResponseDTO(any(RecipeProjection.class))).thenReturn(testRecipeResponseDTO);

        Optional<RecipeResponseDTO> result = recipeService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testRecipeResponseDTO.getName(), result.get().getName());
        verify(repository).findProjectedById(1);
    }

    @Test
    void findById_WhenRecipeDoesNotExist_ShouldReturnEmpty() {

        when(repository.findProjectedById(999)).thenReturn(Optional.empty());

        Optional<RecipeResponseDTO> result = recipeService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findProjectedById(999);
    }

    @Test
    void save_WhenValidRecipe_ShouldCreateRecipe() {

        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenReturn(testRecipe);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(productRepository).findById(1);
        verify(allergenRepository).findAllById(anyList());
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void save_WhenProductNotFound_ShouldThrowException() {

        when(productRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.save(testRecipeRequestDTO);
        });
        verify(productRepository).findById(1);
        verify(repository, never()).save(any(Recipe.class));
    }

    @Test
    void save_ShouldCalculateTotalCost() {

        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);

            assertEquals(new BigDecimal("10.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void update_WhenRecipeExists_ShouldUpdateRecipe() {

        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(testRecipe)).thenReturn(testRecipe);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(testRecipe);
    }

    @Test
    void update_WhenRecipeDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<RecipeResponseDTO> result = recipeService.update(999, testRecipeRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Recipe.class));
    }

    @Test
    void update_ShouldRecalculateTotalCost() {

        testRecipeRequestDTO.getComponents().get(0).setQuantity(new BigDecimal("3.0"));

        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);

            assertEquals(new BigDecimal("15.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void update_WhenComponentsNull_ShouldClearComponents() {

        testRecipeRequestDTO.setComponents(null);

        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(testRecipe)).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertTrue(savedRecipe.getComponents().isEmpty());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        assertTrue(result.isPresent());
    }

    @Test
    void update_WhenAllergensNull_ShouldClearAllergens() {

        testRecipeRequestDTO.setAllergenIds(null);

        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(testRecipe)).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertTrue(savedRecipe.getAllergens().isEmpty());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        assertTrue(result.isPresent());
    }

    @Test
    void deleteById_ShouldCallRepository() {

        doNothing().when(repository).deleteById(1);

        recipeService.deleteById(1);

        verify(repository).deleteById(1);
    }

    @Test
    void findByNameContaining_ShouldReturnMatchingRecipes() {

        when(repository.findByNameContainingIgnoreCaseAndIsHiddenFalse("Test"))
                .thenReturn(Arrays.asList(testProjection));
        when(recipeMapper.toResponseDTO(any(RecipeProjection.class))).thenReturn(testRecipeResponseDTO);

        List<RecipeResponseDTO> result = recipeService.findByNameContaining("Test");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByNameContainingIgnoreCaseAndIsHiddenFalse("Test");
    }

    @Test
    void findByCostLessThan_ShouldReturnRecipesBelowCost() {

        BigDecimal maxCost = new BigDecimal("20.00");
        when(repository.findByTotalCostLessThanAndIsHiddenFalse(maxCost))
                .thenReturn(Arrays.asList(testProjection));
        when(recipeMapper.toResponseDTO(any(RecipeProjection.class))).thenReturn(testRecipeResponseDTO);

        List<RecipeResponseDTO> result = recipeService.findByCostLessThan(maxCost);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByTotalCostLessThanAndIsHiddenFalse(maxCost);
    }

    @Test
    void save_WithMultipleComponents_ShouldCalculateTotalCost() {

        Product product2 = new Product();
        product2.setId(2);
        product2.setName("Product 2");
        product2.setUnitPrice(new BigDecimal("3.00"));

        RecipeComponentRequestDTO component2DTO = new RecipeComponentRequestDTO();
        component2DTO.setProductId(2);
        component2DTO.setQuantity(new BigDecimal("4.0"));
        testRecipeRequestDTO.setComponents(Arrays.asList(
                testRecipeRequestDTO.getComponents().get(0),
                component2DTO));

        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(productRepository.findById(2)).thenReturn(Optional.of(product2));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);

            assertEquals(new BigDecimal("22.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void save_WithEmptyComponents_ShouldHaveZeroCost() {

        testRecipeRequestDTO.setComponents(new ArrayList<>());

        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertEquals(BigDecimal.ZERO.setScale(2), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void cookRecipe_WhenValidRequest_ShouldDeductStockAndReturnRecipe() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("2.0"));
        cookingRequest.setDetails("Test cooking");

        testProduct.setCurrentStock(new BigDecimal("100.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findById(1);
        verify(stockLedgerService).recordStockMovement(
                eq(1),
                argThat(amount -> amount.compareTo(new BigDecimal("4.0").negate()) == 0),
                eq(MovementType.SALIDA),
                anyString(),
                any(),
                isNull());
    }

    @Test
    void cookRecipe_WhenRecipeNotFound_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(999);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        verify(repository).findByIdWithDetails(999);
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenRecipeHasNoComponents_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        Recipe emptyRecipe = new Recipe();
        emptyRecipe.setId(1);
        emptyRecipe.setName("Empty Recipe");
        emptyRecipe.setComponents(new ArrayList<>());

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(emptyRecipe));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        assertTrue(exception.getMessage().contains("ERROR_RECIPE_NO_COMPONENTS"));
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenInsufficientStock_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("10.0"));

        testProduct.setCurrentStock(new BigDecimal("5.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        assertTrue(exception.getMessage().contains("Stock insuficiente"));
        assertTrue(exception.getMessage().contains(testProduct.getName()));
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenProductNotFound_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        verify(productRepository).findById(1);
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WithMultipleComponents_ShouldDeductAllStocks() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        Product product2 = new Product();
        product2.setId(2);
        product2.setName("Test Product 2");
        product2.setUnitPrice(new BigDecimal("3.00"));
        product2.setCurrentStock(new BigDecimal("50.0"));

        RecipeComponent component2 = new RecipeComponent();
        component2.setProduct(product2);
        component2.setQuantity(new BigDecimal("3.0"));
        testRecipe.addComponent(component2);

        testProduct.setCurrentStock(new BigDecimal("100.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(productRepository.findById(2)).thenReturn(Optional.of(product2));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);
        verify(stockLedgerService, times(2)).recordStockMovement(
                anyInt(),
                any(BigDecimal.class),
                eq(MovementType.SALIDA),
                anyString(),
                any(),
                isNull());
    }

    @Test
    void cookRecipe_WithFractionalQuantity_ShouldCalculateCorrectly() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.5"));
        cookingRequest.setDetails("Half and half");

        testProduct.setCurrentStock(new BigDecimal("100.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);

        verify(stockLedgerService).recordStockMovement(
                eq(1),
                argThat(amount -> amount.compareTo(new BigDecimal("3.0").negate()) == 0),
                eq(MovementType.SALIDA),
                anyString(),
                any(),
                isNull());
    }

    @Test
    void save_WithDuplicateProductIds_ShouldMergeQuantities() {

        RecipeComponentRequestDTO comp1 = new RecipeComponentRequestDTO();
        comp1.setProductId(1);
        comp1.setQuantity(new BigDecimal("2.0"));

        RecipeComponentRequestDTO comp2 = new RecipeComponentRequestDTO();
        comp2.setProductId(1);
        comp2.setQuantity(new BigDecimal("3.0"));

        testRecipeRequestDTO.setComponents(Arrays.asList(comp1, comp2));

        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);

            assertEquals(1, savedRecipe.getComponents().size());
            assertEquals(0, new BigDecimal("5.0").compareTo(savedRecipe.getComponents().get(0).getQuantity()));

            assertEquals(new BigDecimal("25.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(productRepository, times(1)).findById(1);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void getRecipeStats_ShouldReturnStats() {
        // Arrange
        when(repository.countByIsHiddenFalse()).thenReturn(10L);
        when(repository.countWithAllergens()).thenReturn(3L);
        when(repository.countWithoutAllergens()).thenReturn(7L);
        when(repository.getAveragePrice()).thenReturn(new BigDecimal("15.50"));

        RecipeStatsResponseDTO expected = RecipeStatsResponseDTO.builder()
                .totalRecipes(10L)
                .recipesWithAllergens(3L)
                .recipesWithoutAllergens(7L)
                .averagePrice(new BigDecimal("15.50"))
                .build();

        when(statsMapper.toRecipeStatsDTO(anyLong(), anyLong(), anyLong(), any(BigDecimal.class)))
                .thenReturn(expected);

        // Act
        RecipeStatsResponseDTO result = recipeService.getRecipeStats();

        // Assert
        assertEquals(expected, result);
        verify(repository).countByIsHiddenFalse();
        verify(repository).countWithAllergens();
        verify(repository).countWithoutAllergens();
        verify(repository).getAveragePrice();
    }
}
