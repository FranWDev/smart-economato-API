package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.ProductRequestDTO;
import com.economatom.inventory.dto.response.ProductResponseDTO;
import com.economatom.inventory.exception.ConcurrencyException;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.mapper.ProductMapper;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.repository.InventoryAuditRepository;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.RecipeComponentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private InventoryAuditRepository movementRepository;

    @Mock
    private RecipeComponentRepository recipeComponentRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private ProductRequestDTO testProductRequestDTO;
    private ProductResponseDTO testProductResponseDTO;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setUnit("KG");
        testProduct.setType("INGREDIENT");
        testProduct.setCurrentStock(new BigDecimal("10.0"));
        testProduct.setUnitPrice(new BigDecimal("5.0"));
        testProduct.setProductCode("TEST001");

        testProductRequestDTO = new ProductRequestDTO();
        testProductRequestDTO.setName("Test Product");
        testProductRequestDTO.setUnit("KG");
        testProductRequestDTO.setType("INGREDIENT");
        testProductRequestDTO.setCurrentStock(new BigDecimal("10.0"));
        testProductRequestDTO.setUnitPrice(new BigDecimal("5.0"));
        testProductRequestDTO.setProductCode("TEST001");

        testProductResponseDTO = new ProductResponseDTO();
        testProductResponseDTO.setId(1);
        testProductResponseDTO.setName("Test Product");
        testProductResponseDTO.setUnit("KG");
        testProductResponseDTO.setType("INGREDIENT");
        testProductResponseDTO.setCurrentStock(new BigDecimal("10.0"));
        testProductResponseDTO.setUnitPrice(new BigDecimal("5.0"));
        testProductResponseDTO.setProductCode("TEST001");
    }

    @Test
    void findAll_ShouldReturnPageOfProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(Arrays.asList(testProduct));
        
        when(repository.findAll(pageable)).thenReturn(page);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        Page<ProductResponseDTO> result = productService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testProductResponseDTO.getName(), result.getContent().get(0).getName());
        verify(repository).findAll(pageable);
    }

    @Test
    void findById_WhenProductExists_ShouldReturnProduct() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        Optional<ProductResponseDTO> result = productService.findById(1);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
    }

    @Test
    void findById_WhenProductDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        Optional<ProductResponseDTO> result = productService.findById(999);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findById(999);
    }

    @Test
    void findByCodebar_WhenProductExists_ShouldReturnProduct() {
        // Arrange
        when(repository.findByProductCode("TEST001")).thenReturn(Optional.of(testProduct));
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        Optional<ProductResponseDTO> result = productService.findByCodebar("TEST001");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getProductCode(), result.get().getProductCode());
        verify(repository).findByProductCode("TEST001");
    }

    @Test
    void save_WhenNameDoesNotExist_ShouldCreateProduct() {
        // Arrange
        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        when(productMapper.toEntity(testProductRequestDTO)).thenReturn(testProduct);
        when(repository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        ProductResponseDTO result = productService.save(testProductRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals(testProductResponseDTO.getName(), result.getName());
        verify(repository).existsByName(testProductRequestDTO.getName());
        verify(repository).save(testProduct);
    }

    @Test
    void save_WhenNameExists_ShouldThrowException() {
        // Arrange
        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            productService.save(testProductRequestDTO);
        });
        verify(repository).existsByName(testProductRequestDTO.getName());
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void save_WhenUnitIsInvalid_ShouldThrowException() {
        // Arrange
        testProductRequestDTO.setUnit("INVALID");
        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.save(testProductRequestDTO);
        });
        assertTrue(exception.getMessage().contains("Unidad de medida inválida"));
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void save_WithValidUnits_ShouldSucceed() {
        // Arrange
        List<String> validUnits = Arrays.asList("KG", "G", "L", "ML", "UND");
        
        for (String unit : validUnits) {
            testProductRequestDTO.setUnit(unit);
            when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
            when(productMapper.toEntity(testProductRequestDTO)).thenReturn(testProduct);
            when(repository.save(testProduct)).thenReturn(testProduct);
            when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

            // Act
            ProductResponseDTO result = productService.save(testProductRequestDTO);

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    void update_WhenProductExists_ShouldUpdateProduct() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);
        doNothing().when(productMapper).updateEntity(testProductRequestDTO, testProduct);

        // Act
        Optional<ProductResponseDTO> result = productService.update(1, testProductRequestDTO);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
        verify(productMapper).updateEntity(testProductRequestDTO, testProduct);
        verify(repository).save(testProduct);
    }

    @Test
    void update_WhenNameChangesAndNewNameExists_ShouldThrowException() {
        // Arrange
        testProduct.setName("Old Name");
        testProductRequestDTO.setName("New Name");
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.existsByName("New Name")).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            productService.update(1, testProductRequestDTO);
        });
        verify(repository).findById(1);
        verify(repository).existsByName("New Name");
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void update_WhenOptimisticLockingFails_ShouldThrowConcurrencyException() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        doNothing().when(productMapper).updateEntity(testProductRequestDTO, testProduct);
        when(repository.save(testProduct)).thenThrow(new OptimisticLockingFailureException("Lock failure"));

        // Act & Assert
        assertThrows(ConcurrencyException.class, () -> {
            productService.update(1, testProductRequestDTO);
        });
        verify(repository).findById(1);
        verify(repository).save(testProduct);
    }

    @Test
    void update_WhenProductDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        Optional<ProductResponseDTO> result = productService.update(999, testProductRequestDTO);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void deleteById_WhenProductHasNoReferences_ShouldDelete() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(false);
        when(recipeComponentRepository.existsByProductId(1)).thenReturn(false);
        doNothing().when(repository).deleteById(1);

        // Act
        productService.deleteById(1);

        // Assert
        verify(repository).findById(1);
        verify(movementRepository).existsByProductId(1);
        verify(recipeComponentRepository).existsByProductId(1);
        verify(repository).deleteById(1);
    }

    @Test
    void deleteById_WhenProductHasMovements_ShouldThrowException() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(true);

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.deleteById(1);
        });
        assertTrue(exception.getMessage().contains("movimientos de inventario"));
        verify(repository, never()).deleteById(anyInt());
    }

    @Test
    void deleteById_WhenProductUsedInRecipes_ShouldThrowException() {
        // Arrange
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(false);
        when(recipeComponentRepository.existsByProductId(1)).thenReturn(true);

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.deleteById(1);
        });
        assertTrue(exception.getMessage().contains("utilizado en recetas"));
        verify(repository, never()).deleteById(anyInt());
    }

    @Test
    void deleteById_WhenProductDoesNotExist_ShouldDoNothing() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act
        productService.deleteById(999);

        // Assert
        verify(repository).findById(999);
        verify(repository, never()).deleteById(anyInt());
    }

    @Test
    void findByType_ShouldReturnProductsOfType() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(repository.findByType("INGREDIENT")).thenReturn(products);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        List<ProductResponseDTO> result = productService.findByType("INGREDIENT");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testProductResponseDTO.getType(), result.get(0).getType());
        verify(repository).findByType("INGREDIENT");
    }

    @Test
    void findByNameContaining_ShouldReturnMatchingProducts() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(repository.findByNameContainingIgnoreCase("Test")).thenReturn(products);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        List<ProductResponseDTO> result = productService.findByNameContaining("Test");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getName().contains("Test"));
        verify(repository).findByNameContainingIgnoreCase("Test");
    }

    @Test
    void findByStockLessThan_ShouldReturnLowStockProducts() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        BigDecimal threshold = new BigDecimal("20.0");
        when(repository.findByCurrentStockLessThan(threshold)).thenReturn(products);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        List<ProductResponseDTO> result = productService.findByStockLessThan(threshold);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getCurrentStock().compareTo(threshold) < 0);
        verify(repository).findByCurrentStockLessThan(threshold);
    }

    @Test
    void findByPriceRange_ShouldReturnProductsInRange() {
        // Arrange
        BigDecimal min = new BigDecimal("1.0");
        BigDecimal max = new BigDecimal("10.0");
        List<Product> products = Arrays.asList(testProduct);
        when(repository.findByUnitPriceBetween(min, max)).thenReturn(products);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        // Act
        List<ProductResponseDTO> result = productService.findByPriceRange(min, max);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByUnitPriceBetween(min, max);
    }
}
