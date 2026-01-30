package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.RecipeComponentRequestDTO;
import com.economatom.inventory.dto.request.RecipeRequestDTO;
import com.economatom.inventory.dto.response.RecipeResponseDTO;
import com.economatom.inventory.exception.ResourceNotFoundException;
import com.economatom.inventory.mapper.RecipeMapper;
import com.economatom.inventory.model.Allergen;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.Recipe;
import com.economatom.inventory.model.RecipeComponent;
import com.economatom.inventory.repository.AllergenRepository;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

    @InjectMocks
    private RecipeService recipeService;

    private Recipe testRecipe;
    private RecipeRequestDTO testRecipeRequestDTO;
    private RecipeResponseDTO testRecipeResponseDTO;
    private Product testProduct;
    private Allergen testAllergen;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void findAll_ShouldReturnListOfRecipes() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Recipe> page = new PageImpl<>(Arrays.asList(testRecipe));
        when(repository.findAll(pageable)).thenReturn(page);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        List<RecipeResponseDTO> result = recipeService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findAll(pageable);
    }

    @Test
    void findById_WhenRecipeExists_ShouldReturnRecipe() {
        // Arrange
        when(repository.findByIdWithDetails(1)).thenReturn(Optional.of(testRecipe));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        Optional<RecipeResponseDTO> result = recipeService.findById(1);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRecipeResponseDTO.getName(), result.get().getName());
        verify(repository).findByIdWithDetails(1);
    }

    @Test
    void findById_WhenRecipeDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findByIdWithDetails(999)).thenReturn(Optional.empty());

        // Act
        Optional<RecipeResponseDTO> result = recipeService.findById(999);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findByIdWithDetails(999);
    }

    @Test
    void save_WhenValidRecipe_ShouldCreateRecipe() {
        // Arrange
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenReturn(testRecipe);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        // Assert
        assertNotNull(result);
        verify(productRepository).findById(1);
        verify(allergenRepository).findAllById(anyList());
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void save_WhenProductNotFound_ShouldThrowException() {
        // Arrange
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            recipeService.save(testRecipeRequestDTO);
        });
        verify(productRepository).findById(1);
        verify(repository, never()).save(any(Recipe.class));
    }

    @Test
    void save_ShouldCalculateTotalCost() {
        // Arrange
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            // El total debe ser 2.0 * 5.00 = 10.00
            assertEquals(new BigDecimal("10.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        // Act
        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        // Assert
        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void update_WhenRecipeExists_ShouldUpdateRecipe() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(testRecipe)).thenReturn(testRecipe);
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        // Assert
        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(repository).save(testRecipe);
    }

    @Test
    void update_WhenRecipeDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        Optional<RecipeResponseDTO> result = recipeService.update(999, testRecipeRequestDTO);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Recipe.class));
    }

    @Test
    void update_ShouldRecalculateTotalCost() {
        // Arrange
        testRecipeRequestDTO.getComponents().get(0).setQuantity(new BigDecimal("3.0"));
        
        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            // El total debe ser 3.0 * 5.00 = 15.00
            assertEquals(new BigDecimal("15.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        // Act
        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        // Assert
        assertTrue(result.isPresent());
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void update_WhenComponentsNull_ShouldClearComponents() {
        // Arrange
        testRecipeRequestDTO.setComponents(null);
        
        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(testRecipe)).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertTrue(savedRecipe.getComponents().isEmpty());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        // Assert
        assertTrue(result.isPresent());
    }

    @Test
    void update_WhenAllergensNull_ShouldClearAllergens() {
        // Arrange
        testRecipeRequestDTO.setAllergenIds(null);
        
        when(repository.findById(1)).thenReturn(Optional.of(testRecipe));
        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(testRecipe)).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertTrue(savedRecipe.getAllergens().isEmpty());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        Optional<RecipeResponseDTO> result = recipeService.update(1, testRecipeRequestDTO);

        // Assert
        assertTrue(result.isPresent());
    }

    @Test
    void deleteById_ShouldCallRepository() {
        // Arrange
        doNothing().when(repository).deleteById(1);

        // Act
        recipeService.deleteById(1);

        // Assert
        verify(repository).deleteById(1);
    }

    @Test
    void findByNameContaining_ShouldReturnMatchingRecipes() {
        // Arrange
        when(repository.findByNameContainingIgnoreCaseWithDetails("Test")).thenReturn(Arrays.asList(testRecipe));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        List<RecipeResponseDTO> result = recipeService.findByNameContaining("Test");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByNameContainingIgnoreCaseWithDetails("Test");
    }

    @Test
    void findByCostLessThan_ShouldReturnRecipesBelowCost() {
        // Arrange
        BigDecimal maxCost = new BigDecimal("20.00");
        when(repository.findByTotalCostLessThanWithDetails(maxCost)).thenReturn(Arrays.asList(testRecipe));
        when(recipeMapper.toResponseDTO(testRecipe)).thenReturn(testRecipeResponseDTO);

        // Act
        List<RecipeResponseDTO> result = recipeService.findByCostLessThan(maxCost);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByTotalCostLessThanWithDetails(maxCost);
    }

    @Test
    void save_WithMultipleComponents_ShouldCalculateTotalCost() {
        // Arrange
        Product product2 = new Product();
        product2.setId(2);
        product2.setName("Product 2");
        product2.setUnitPrice(new BigDecimal("3.00"));

        RecipeComponentRequestDTO component2DTO = new RecipeComponentRequestDTO();
        component2DTO.setProductId(2);
        component2DTO.setQuantity(new BigDecimal("4.0"));
        testRecipeRequestDTO.setComponents(Arrays.asList(
            testRecipeRequestDTO.getComponents().get(0),
            component2DTO
        ));

        when(productRepository.findById(1)).thenReturn(Optional.of(testProduct));
        when(productRepository.findById(2)).thenReturn(Optional.of(product2));
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            // Total: (2.0 * 5.00) + (4.0 * 3.00) = 10.00 + 12.00 = 22.00
            assertEquals(new BigDecimal("22.00"), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        // Act
        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        // Assert
        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    void save_WithEmptyComponents_ShouldHaveZeroCost() {
        // Arrange
        testRecipeRequestDTO.setComponents(new ArrayList<>());
        
        when(allergenRepository.findAllById(anyList())).thenReturn(Arrays.asList(testAllergen));
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> {
            Recipe savedRecipe = invocation.getArgument(0);
            assertEquals(BigDecimal.ZERO.setScale(2), savedRecipe.getTotalCost());
            return savedRecipe;
        });
        when(recipeMapper.toResponseDTO(any(Recipe.class))).thenReturn(testRecipeResponseDTO);

        // Act
        RecipeResponseDTO result = recipeService.save(testRecipeRequestDTO);

        // Assert
        assertNotNull(result);
        verify(repository).save(any(Recipe.class));
    }
}
