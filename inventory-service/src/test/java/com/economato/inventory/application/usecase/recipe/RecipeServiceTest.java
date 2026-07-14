package com.economato.inventory.application.usecase.recipe;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.economato.inventory.application.dto.recipe.projection.RecipeProjection;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeStatsResponseDTO;
import com.economato.inventory.application.mapper.recipe.RecipeMapper;
import com.economato.inventory.application.mapper.shared.StatsMapper;
import com.economato.inventory.domain.model.recipe.Allergen;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

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

    @Mock
    private SecurityContextHelper securityContextHelper;

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
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
        Mockito.lenient().when(i18nService.getMessage(eq(MessageKey.ERROR_RECIPE_NOT_FOUND), any(Object[].class)))
                .thenAnswer(invocation -> "ERROR_RECIPE_NOT_FOUND " + Arrays.toString((Object[]) invocation.getArgument(1)));
        Mockito.lenient().when(i18nService.getMessage(eq(MessageKey.ERROR_PRODUCT_NOT_FOUND), any(Object[].class)))
                .thenAnswer(invocation -> "ERROR_PRODUCT_NOT_FOUND " + Arrays.toString((Object[]) invocation.getArgument(1)));
        Mockito.lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object arg = invocation.getArgument(1);
                String argsStr = arg instanceof Object[] ? Arrays.toString((Object[]) arg) : String.valueOf(arg);
                return ((MessageKey) invocation.getArgument(0)).name() + " " + (argsStr != null ? argsStr : "[]");
            });
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
        testRecipe.setComponents(new HashSet<>());
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

        User testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(testUser);

        recipeService = new RecipeService(i18nService, repository,
                productRepository,
                allergenRepository,
                recipeMapper,
                null,
                statsMapper,
                stockLedgerService,
                null,
                securityContextHelper,
                null,
                null,
                null);
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

        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenReturn(testRecipe);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(productRepository).findAllById(any());
        verify(allergenRepository).findAllById(anyList());
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void save_WhenProductNotFound_ShouldThrowException() {

        when(productRepository.findAllById(any())).thenReturn(Arrays.asList());

        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.save(testRecipeRequestDTO);
        });
        verify(productRepository).findAllById(any());
        verify(repository, never()).save(any(Recipe.class));
    }

    @Test
    void save_ShouldCalculateTotalCost() {

        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
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

        when(repository.existsById(1)).thenReturn(true);
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

        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct, product2));
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);
        verify(repository).findByIdWithDetails(1);
        verify(productRepository).findAllById(any());
        verify(stockLedgerService).recordBatchStockMovements(
            argThat((List<BatchMovementItem> items) ->
                items.size() == 1
                    && items.get(0).getProductId().equals(1)
                    && items.get(0).getMovementType() == MovementType.SALIDA
                    && items.get(0).getQuantityDelta().compareTo(new BigDecimal("-4.000")) == 0),
            any(User.class),
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
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenRecipeHasNoComponents_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        Recipe emptyRecipe = new Recipe();
        emptyRecipe.setId(1);
        emptyRecipe.setName("Empty Recipe");
        emptyRecipe.setComponents(new HashSet<>());

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(emptyRecipe));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        assertTrue(exception.getMessage().contains("ERROR_RECIPE_NO_COMPONENTS"));
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenInsufficientStock_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("10.0"));

        testProduct.setCurrentStock(new BigDecimal("5.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        assertTrue(exception.getMessage().contains("ERROR_RECIPE_STOCK_INSUFFICIENT"));
        assertTrue(exception.getMessage().contains(testProduct.getName()));
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cookRecipe_WhenProductNotFound_ShouldThrowException() {

        RecipeCookingRequestDTO cookingRequest = new RecipeCookingRequestDTO();
        cookingRequest.setRecipeId(1);
        cookingRequest.setQuantity(new BigDecimal("1.0"));

        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList());

        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.cookRecipe(cookingRequest);
        });

        verify(productRepository).findAllById(any());
        verify(stockLedgerService, never()).recordStockMovement(anyInt(), any(), any(), any(), any(), any(), any(), any());
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct, product2));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);
        verify(stockLedgerService).recordBatchStockMovements(
            argThat((List<BatchMovementItem> items) ->
                items.size() == 2
                    && items.stream().allMatch(i -> i.getMovementType() == MovementType.SALIDA)),
            any(User.class),
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
        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.cookRecipe(cookingRequest);

        assertNotNull(result);

        verify(stockLedgerService).recordBatchStockMovements(
            argThat((List<BatchMovementItem> items) ->
                items.size() == 1
                    && items.get(0).getProductId().equals(1)
                    && items.get(0).getMovementType() == MovementType.SALIDA
                    && items.get(0).getQuantityDelta().compareTo(new BigDecimal("-3.000")) == 0),
            any(User.class),
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

        when(productRepository.findAllById(any())).thenReturn(Arrays.asList(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);

            assertEquals(1, savedRecipe.getComponents().size());
            assertEquals(0, new BigDecimal("5.0").compareTo(savedRecipe.getComponents().iterator().next().getQuantity()));

            assertEquals(new BigDecimal("25.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        assertNotNull(result);
        verify(productRepository, times(1)).findAllById(any());
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

    @Test
    void recalculateRecipesUsingProduct_ShouldUpdateCostWhenProductPriceChanges() {
        // Arrange
        testProduct.setUnitPrice(new BigDecimal("20.00")); // Change price from 5.0 to 20.0
        // testRecipe has 2.0 units of testProduct. New cost = 2.0 * 20.0 = 40.00
        
        when(repository.findByComponentsProductIdWithDetails(1)).thenReturn(Arrays.asList(testRecipe));
        
        // Act
        recipeService.recalculateRecipesUsingProduct(1);
        
        // Assert
        assertEquals(new BigDecimal("40.00"), testRecipe.getTotalCost());
        verify(repository).saveAll(Collections.singletonList(testRecipe));
    }
}
