package com.economatom.inventory.service;

import com.economatom.inventory.annotation.ProductAuditable;
import com.economatom.inventory.dto.request.ProductRequestDTO;
import com.economatom.inventory.dto.response.ProductResponseDTO;
import com.economatom.inventory.exception.ConcurrencyException;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.mapper.ProductMapper;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.InventoryAuditRepository;
import com.economatom.inventory.repository.RecipeComponentRepository;
import com.economatom.inventory.repository.SupplierRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
public class ProductService {

    private final ProductRepository repository;
    private final InventoryAuditRepository movementRepository;
    private final RecipeComponentRepository recipeComponentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;

    public ProductService(
            ProductRepository repository,
            InventoryAuditRepository movementRepository,
            RecipeComponentRepository recipeComponentRepository,
            SupplierRepository supplierRepository,
            ProductMapper productMapper) {
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.recipeComponentRepository = recipeComponentRepository;
        this.supplierRepository = supplierRepository;
        this.productMapper = productMapper;
    }

    // CORREGIDO: Ahora devuelve Page<ProductResponseDTO> en lugar de List
    // NO cachear Page (PageImpl no es serializable con Jackson)
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "product", key = "#id")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findById(Integer id) {
        return repository.findById(id)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "product", key = "'code:' + #codebar")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findByCodebar(String codebar) {
        return repository.findByProductCode(codebar)
                .map(productMapper::toResponseDTO);
    }

    @CacheEvict(value = {"products", "product"}, allEntries = true)
    @ProductAuditable(action = "CREATE_PRODUCT")
    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
    public ProductResponseDTO save(ProductRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException("Ya existe un producto con ese nombre");
        }

        validateProductData(requestDTO);
        Product product = productMapper.toEntity(requestDTO);
        return productMapper.toResponseDTO(repository.save(product));
    }

    @CacheEvict(value = {"products", "product"}, allEntries = true)
    @ProductAuditable(action = "UPDATE_PRODUCT")
    @Retryable(
        retryFor = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional(
        rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class},
        isolation = Isolation.REPEATABLE_READ
    )
    public Optional<ProductResponseDTO> update(Integer id, ProductRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.existsByName(requestDTO.getName())) {
                        throw new InvalidOperationException("Ya existe un producto con ese nombre");
                    }
                    validateProductData(requestDTO);
                    productMapper.updateEntity(requestDTO, existing);
                    
                    try {
                        return productMapper.toResponseDTO(repository.save(existing));
                    } catch (OptimisticLockingFailureException ex) {
                        throw new ConcurrencyException("Product", id);
                    }
                });
    }

    @CacheEvict(value = {"products", "product"}, allEntries = true)
    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
    public void deleteById(Integer id) {
        repository.findById(id).ifPresent(product -> {
            if (movementRepository.existsByProductId(id)) {
                throw new InvalidOperationException(
                        "No se puede eliminar el producto porque tiene movimientos de inventario asociados");
            }
            if (recipeComponentRepository.existsByProductId(id)) {
                throw new InvalidOperationException(
                        "No se puede eliminar el producto porque es utilizado en recetas");
            }
            repository.deleteById(id);
        });
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByType(String type) {
        return repository.findByType(type).stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByNameContaining(String namePart) {
        return repository.findByNameContainingIgnoreCase(namePart).stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByStockLessThan(BigDecimal stock) {
        return repository.findByCurrentStockLessThan(stock).stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByPriceRange(BigDecimal min, BigDecimal max) {
        return repository.findByUnitPriceBetween(min, max).stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private void validateProductData(ProductRequestDTO requestDTO) {
        if (!isValidUnit(requestDTO.getUnit())) {
            throw new InvalidOperationException(
                    "Unidad de medida inválida. Debe ser: KG, G, L, ML o UND");
        }
        
        // Validar que el supplier existe si se proporciona
        if (requestDTO.getSupplierId() != null) {
            if (!supplierRepository.existsById(requestDTO.getSupplierId())) {
                throw new InvalidOperationException(
                        "El proveedor especificado no existe");
            }
        }
    }

    private boolean isValidUnit(String unit) {
        return unit != null && List.of("KG", "G", "L", "ML", "UND").contains(unit.toUpperCase());
    }
}