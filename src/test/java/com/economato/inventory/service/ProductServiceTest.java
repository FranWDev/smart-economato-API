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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.economato.inventory.dto.projection.ProductProjection;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.exception.ConcurrencyException;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.ProductMapper;
import com.economato.inventory.model.Product;
import com.economato.inventory.repository.InventoryAuditRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeComponentRepository;
import com.economato.inventory.repository.SupplierRepository;
import com.economato.inventory.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.economato.inventory.security.SecurityContextHelper;
import com.economato.inventory.model.User;

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
    private SupplierRepository supplierRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private StockLedgerService stockLedgerService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private I18nService i18nService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private ProductRequestDTO testProductRequestDTO;
    private ProductResponseDTO testProductResponseDTO;
    private ProductProjection testProjection;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(i18nService.getMessage(ArgumentMatchers.any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
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

        testProjection = mock(ProductProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getName()).thenReturn("Test Product");
        lenient().when(testProjection.getUnit()).thenReturn("KG");
        lenient().when(testProjection.getType()).thenReturn("INGREDIENT");
        lenient().when(testProjection.getCurrentStock()).thenReturn(new BigDecimal("10.0"));
        lenient().when(testProjection.getUnitPrice()).thenReturn(new BigDecimal("5.0"));
        lenient().when(testProjection.getProductCode()).thenReturn("TEST001");

        User testUser = new User();
        testUser.setId(1);
        testUser.setName("Test User");
        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void findAll_ShouldReturnPageOfProducts() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductProjection> page = new PageImpl<>(Arrays.asList(testProjection));

        when(repository.findByIsHiddenFalse(pageable)).thenReturn(page);
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        Page<ProductResponseDTO> result = productService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testProductResponseDTO.getName(), result.getContent().get(0).getName());
        verify(repository).findByIsHiddenFalse(pageable);
    }

    @Test
    void findById_WhenProductExists_ShouldReturnProduct() {

        when(repository.findProjectedById(1)).thenReturn(Optional.of(testProjection));
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        Optional<ProductResponseDTO> result = productService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getName(), result.get().getName());
        verify(repository).findProjectedById(1);
    }

    @Test
    void findById_WhenProductDoesNotExist_ShouldReturnEmpty() {

        when(repository.findProjectedById(999)).thenReturn(Optional.empty());

        Optional<ProductResponseDTO> result = productService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findProjectedById(999);
    }

    @Test
    void findByCodebar_WhenProductExists_ShouldReturnProduct() {

        when(repository.findProjectedByProductCode("TEST001")).thenReturn(Optional.of(testProjection));
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        Optional<ProductResponseDTO> result = productService.findByCodebar("TEST001");

        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getProductCode(), result.get().getProductCode());
        verify(repository).findProjectedByProductCode("TEST001");
    }

    @Test
    void findByName_ShouldReturnPageOfMatchingProducts() {
        // Arrange
        ProductProjection proj1 = mock(ProductProjection.class);
        lenient().when(proj1.getId()).thenReturn(1);
        lenient().when(proj1.getName()).thenReturn("Leche Desnatada");
        lenient().when(proj1.getProductCode()).thenReturn("CODE001");

        ProductProjection proj2 = mock(ProductProjection.class);
        lenient().when(proj2.getId()).thenReturn(2);
        lenient().when(proj2.getName()).thenReturn("Leche Entera");
        lenient().when(proj2.getProductCode()).thenReturn("CODE002");

        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductProjection> page = new PageImpl<>(Arrays.asList(proj1, proj2));

        when(repository.findByNameContainingIgnoreCaseAndIsHiddenFalse("leche", pageable)).thenReturn(page);

        ProductResponseDTO resp1 = new ProductResponseDTO();
        resp1.setName("Leche Desnatada");
        ProductResponseDTO resp2 = new ProductResponseDTO();
        resp2.setName("Leche Entera");

        when(productMapper.toResponseDTO(proj1)).thenReturn(resp1);
        when(productMapper.toResponseDTO(proj2)).thenReturn(resp2);

        // Act
        Page<ProductResponseDTO> result = productService.findByName("leche", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("Leche Desnatada", result.getContent().get(0).getName());
        assertEquals("Leche Entera", result.getContent().get(1).getName());
        verify(repository).findByNameContainingIgnoreCaseAndIsHiddenFalse("leche", pageable);
    }

    @Test
    void findByName_WhenNoMatches_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductProjection> emptyPage = new PageImpl<>(Arrays.asList());

        when(repository.findByNameContainingIgnoreCaseAndIsHiddenFalse("inexistente", pageable))
                .thenReturn(emptyPage);

        // Act
        Page<ProductResponseDTO> result = productService.findByName("inexistente", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        verify(repository).findByNameContainingIgnoreCaseAndIsHiddenFalse("inexistente", pageable);
    }

    @Test
    void save_WhenNameDoesNotExist_ShouldCreateProduct() {

        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        when(productMapper.toEntity(testProductRequestDTO)).thenReturn(testProduct);
        when(repository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

        ProductResponseDTO result = productService.save(testProductRequestDTO);

        assertNotNull(result);
        assertEquals(testProductResponseDTO.getName(), result.getName());
        verify(repository).existsByName(testProductRequestDTO.getName());
        verify(repository).save(testProduct);
    }

    @Test
    void save_WhenNameExists_ShouldThrowException() {

        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            productService.save(testProductRequestDTO);
        });
        verify(repository).existsByName(testProductRequestDTO.getName());
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void save_WhenUnitIsInvalid_ShouldThrowException() {

        testProductRequestDTO.setUnit("INVALID");
        when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.save(testProductRequestDTO);
        });
        assertTrue(exception.getMessage().contains(MessageKey.ERROR_PRODUCT_INVALID_UNIT.name()));
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void save_WithValidUnits_ShouldSucceed() {

        List<String> validUnits = Arrays.asList("KG", "G", "L", "ML", "UND");

        for (String unit : validUnits) {
            testProductRequestDTO.setUnit(unit);
            when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
            when(productMapper.toEntity(testProductRequestDTO)).thenReturn(testProduct);
            when(repository.save(testProduct)).thenReturn(testProduct);
            when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);

            ProductResponseDTO result = productService.save(testProductRequestDTO);

            assertNotNull(result);
        }
    }

    @Test
    void update_WhenProductExists_ShouldUpdateProduct() {

        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toResponseDTO(testProduct)).thenReturn(testProductResponseDTO);
        doNothing().when(productMapper).updateEntity(testProductRequestDTO, testProduct);

        Optional<ProductResponseDTO> result = productService.update(1, testProductRequestDTO);

        assertTrue(result.isPresent());
        assertEquals(testProductResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
        verify(productMapper).updateEntity(testProductRequestDTO, testProduct);
        verify(repository).save(testProduct);
    }

    @Test
    void update_WhenNameChangesAndNewNameExists_ShouldThrowException() {

        testProduct.setName("Old Name");
        testProductRequestDTO.setName("New Name");
        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(repository.existsByName("New Name")).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            productService.update(1, testProductRequestDTO);
        });
        verify(repository).findById(1);
        verify(repository).existsByName("New Name");
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void update_WhenOptimisticLockingFails_ShouldThrowConcurrencyException() {

        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        doNothing().when(productMapper).updateEntity(testProductRequestDTO, testProduct);
        when(repository.save(testProduct)).thenThrow(new OptimisticLockingFailureException("Lock failure"));

        assertThrows(ConcurrencyException.class, () -> {
            productService.update(1, testProductRequestDTO);
        });
        verify(repository).findById(1);
        verify(repository).save(testProduct);
    }

    @Test
    void update_WhenProductDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<ProductResponseDTO> result = productService.update(999, testProductRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void deleteById_WhenProductHasNoReferences_ShouldDelete() {

        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(false);
        when(recipeComponentRepository.existsByProductId(1)).thenReturn(false);
        doNothing().when(repository).delete(testProduct);

        productService.deleteById(1);

        verify(repository).findById(1);
        verify(movementRepository).existsByProductId(1);
        verify(recipeComponentRepository).existsByProductId(1);
        verify(repository).delete(testProduct);
    }

    @Test
    void deleteById_WhenProductHasMovements_ShouldThrowException() {

        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(true);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.deleteById(1);
        });
        assertTrue(exception.getMessage().contains(MessageKey.ERROR_PRODUCT_DELETE_HAS_MOVEMENTS.name()));
        verify(repository, never()).delete(any(Product.class));
    }

    @Test
    void deleteById_WhenProductUsedInRecipes_ShouldThrowException() {

        when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        when(movementRepository.existsByProductId(1)).thenReturn(false);
        when(recipeComponentRepository.existsByProductId(1)).thenReturn(true);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            productService.deleteById(1);
        });
        assertTrue(exception.getMessage().contains(MessageKey.ERROR_PRODUCT_DELETE_IN_RECIPE.name()));
        verify(repository, never()).delete(any(Product.class));
    }

    @Test
    void deleteById_WhenProductDoesNotExist_ShouldDoNothing() {
        assertThrows(ResourceNotFoundException.class, () -> {
            productService.deleteById(999);
        });

        verify(repository).findById(999);
        verify(repository, never()).delete(any(Product.class));
    }

    @Test
    void findByType_ShouldReturnProductsOfType() {

        List<ProductProjection> products = Arrays.asList(testProjection);
        when(repository.findByTypeAndIsHiddenFalse("INGREDIENT")).thenReturn(products);
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        List<ProductResponseDTO> result = productService.findByType("INGREDIENT");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testProductResponseDTO.getType(), result.get(0).getType());
        verify(repository).findByTypeAndIsHiddenFalse("INGREDIENT");
    }

    @Test
    void findByNameContaining_ShouldReturnMatchingProducts() {

        List<ProductProjection> products = Arrays.asList(testProjection);
        when(repository.findByNameContainingIgnoreCaseAndIsHiddenFalse("Test")).thenReturn(products);
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        List<ProductResponseDTO> result = productService.findByNameContaining("Test");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getName().contains("Test"));
        verify(repository).findByNameContainingIgnoreCaseAndIsHiddenFalse("Test");
    }

    @Test
    void findByStockLessThan_ShouldReturnLowStockProducts() {

        List<ProductProjection> products = Arrays.asList(testProjection);
        BigDecimal threshold = new BigDecimal("20.0");
        when(repository.findByCurrentStockLessThanAndIsHiddenFalse(threshold)).thenReturn(products);
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        List<ProductResponseDTO> result = productService.findByStockLessThan(threshold);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getCurrentStock().compareTo(threshold) < 0);
        verify(repository).findByCurrentStockLessThanAndIsHiddenFalse(threshold);
    }

    @Test
    void findByPriceRange_ShouldReturnProductsInRange() {

        BigDecimal min = new BigDecimal("1.0");
        BigDecimal max = new BigDecimal("10.0");
        List<ProductProjection> products = Arrays.asList(testProjection);
        when(repository.findByUnitPriceBetweenAndIsHiddenFalse(min, max)).thenReturn(products);
        when(productMapper.toResponseDTO(any(ProductProjection.class))).thenReturn(testProductResponseDTO);

        List<ProductResponseDTO> result = productService.findByPriceRange(min, max);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByUnitPriceBetweenAndIsHiddenFalse(min, max);
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateStockManually_WhenStockIncreases_ShouldRecordMovement() {

        testProduct.setCurrentStock(new BigDecimal("10.0"));

        testProductRequestDTO.setCurrentStock(new BigDecimal("50.0"));

        Product updatedProduct = new Product();
        updatedProduct.setId(1);
        updatedProduct.setCurrentStock(new BigDecimal("50.0"));

        lenient().when(repository.findById(1)).thenReturn(Optional.of(testProduct), Optional.of(updatedProduct));
        lenient().when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        lenient().when(userRepository.findByName(anyString())).thenReturn(Optional.empty());
        lenient().when(productMapper.toResponseDTO(any(Product.class))).thenReturn(testProductResponseDTO);

        Optional<ProductResponseDTO> result = productService.updateStockManually(1, testProductRequestDTO);

        assertTrue(result.isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateStockManually_WhenStockDecreases_ShouldRecordMovement() {

        testProduct.setCurrentStock(new BigDecimal("100.0"));

        testProductRequestDTO.setCurrentStock(new BigDecimal("60.0"));

        Product updatedProduct = new Product();
        updatedProduct.setId(1);
        updatedProduct.setCurrentStock(new BigDecimal("60.0"));

        lenient().when(repository.findById(1)).thenReturn(Optional.of(testProduct), Optional.of(updatedProduct));
        lenient().when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        lenient().when(userRepository.findByName(anyString())).thenReturn(Optional.empty());
        lenient().when(productMapper.toResponseDTO(any(Product.class))).thenReturn(testProductResponseDTO);

        Optional<ProductResponseDTO> result = productService.updateStockManually(1, testProductRequestDTO);

        assertTrue(result.isPresent());
    }

    @Test
    void updateStockManually_WhenStockUnchanged_ShouldNotRecordMovement() {

        testProduct.setCurrentStock(new BigDecimal("10.0"));

        testProductRequestDTO.setCurrentStock(new BigDecimal("10.0"));

        lenient().when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        lenient().when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        lenient().when(repository.save(any(Product.class))).thenReturn(testProduct);
        lenient().when(productMapper.toResponseDTO(any(Product.class))).thenReturn(testProductResponseDTO);

        Optional<ProductResponseDTO> result = productService.updateStockManually(1, testProductRequestDTO);

        assertTrue(result.isPresent());
    }

    @Test
    void updateStockManually_WhenProductDoesNotExist_ShouldReturnEmpty() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<ProductResponseDTO> result = productService.updateStockManually(999, testProductRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void updateStockManually_WhenOptimisticLockingFails_ShouldThrowConcurrencyException() {
        testProduct.setCurrentStock(new BigDecimal("10.0"));
        testProductRequestDTO.setCurrentStock(new BigDecimal("10.0")); // Sin cambio de stock

        lenient().when(repository.findById(1)).thenReturn(Optional.of(testProduct));
        lenient().when(repository.existsByName(testProductRequestDTO.getName())).thenReturn(false);
        lenient().when(repository.save(any(Product.class)))
                .thenThrow(new OptimisticLockingFailureException("Lock failure"));

        assertThrows(ConcurrencyException.class, () -> {
            productService.updateStockManually(1, testProductRequestDTO);
        });
    }
}
