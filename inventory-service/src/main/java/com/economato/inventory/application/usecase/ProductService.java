package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.ProductAuditable;
import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.request.ProductRequestDTO;
import com.economato.inventory.application.dto.response.ProductResponseDTO;
import com.economato.inventory.application.dto.response.ProductStatsResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.ConcurrencyException;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.economato.inventory.application.mapper.ProductMapper;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.InventoryAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeComponentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
public class ProductService {
    private final I18nService i18nService;

    private static final Set<String> VALID_UNITS = Set.of(
            "KG", "G", "L", "ML", "UNIDAD", "MANOJO", "BOTE",
            "PAQUETE", "SOBRE", "HOJA", "TUBO", "UDS", "UND");

    private final ProductRepository repository;
    private final InventoryAuditRepository movementRepository;
    private final RecipeComponentRepository recipeComponentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;
    private final StockLedgerService stockLedgerService;
    private final SecurityContextHelper securityContextHelper;

    public ProductService(I18nService i18nService,
            ProductRepository repository,
            InventoryAuditRepository movementRepository,
            RecipeComponentRepository recipeComponentRepository,
            SupplierRepository supplierRepository,
            ProductMapper productMapper,
            StockLedgerService stockLedgerService,
            SecurityContextHelper securityContextHelper) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.recipeComponentRepository = recipeComponentRepository;
        this.supplierRepository = supplierRepository;
        this.productMapper = productMapper;
        this.stockLedgerService = stockLedgerService;
        this.securityContextHelper = securityContextHelper;
    }

    @Cacheable(value = "products_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(Pageable pageable) {
        Page<ProductResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(productMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "product", key = "#id")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "product", key = "'code:' + #codebar")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findByCodebar(String codebar) {
        return repository.findProjectedByProductCode(codebar)
                .map(productMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findByName(String namePart, Pageable pageable) {
        return repository.findByNameContainingIgnoreCaseAndIsHiddenFalse(namePart, pageable)
                .map(productMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findProductsWithLedger(String name, Pageable pageable) {
        String normalizedName = (name == null || name.isBlank()) ? null : name.trim();
        Page<ProductResponseDTO> page;
        if (normalizedName == null) {
            page = repository.findProductsWithLedger(pageable)
                    .map(productMapper::toResponseDTO);
        } else {
            page = repository.findProductsWithLedgerByName(normalizedName, pageable)
                    .map(productMapper::toResponseDTO);
        }
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    @CacheEvict(value = { "products_page", "product" }, allEntries = true)
    @ProductAuditable(action = "CREATE_PRODUCT")
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public ProductResponseDTO save(ProductRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_ALREADY_EXISTS));
        }

        validateProductData(requestDTO);
        Product product = productMapper.toEntity(requestDTO);
        return productMapper.toResponseDTO(repository.save(product));
    }

    @CacheEvict(value = { "products_page", "product" }, allEntries = true)
    @ProductAuditable(action = "UPDATE_PRODUCT")
    @Retryable(includes = { OptimisticLockingFailureException.class }, maxRetries = 3, delay = 100)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class,
            Exception.class }, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProductResponseDTO> update(Integer id, ProductRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.existsByName(requestDTO.getName())) {
                        throw new InvalidOperationException(
                                i18nService.getMessage(MessageKey.ERROR_PRODUCT_ALREADY_EXISTS));
                    }
                    validateProductData(requestDTO);
                    productMapper.updateEntity(requestDTO, existing);

                    try {
                        return productMapper.toResponseDTO(repository.save(existing));
                    } catch (OptimisticLockingFailureException ex) {
                        throw new ConcurrencyException(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK), id);
                    }
                });
    }

    @CacheEvict(value = { "products_page", "product" }, allEntries = true)
    @Deprecated(since = "2026-03", forRemoval = false)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));

        if (movementRepository.existsByProductId(id)) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_PRODUCT_DELETE_HAS_MOVEMENTS));
        }
        if (recipeComponentRepository.existsByProductId(id)) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_PRODUCT_DELETE_IN_RECIPE));
        }
        repository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByType(String type) {
        return repository.findByTypeAndIsHiddenFalse(type).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByNameContaining(String namePart) {
        return repository.findByNameContainingIgnoreCaseAndIsHiddenFalse(namePart).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByStockLessThan(BigDecimal stock) {
        return repository.findByCurrentStockLessThanAndIsHiddenFalse(stock).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByPriceRange(BigDecimal min, BigDecimal max) {
        return repository.findByUnitPriceBetweenAndIsHiddenFalse(min, max).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    private void validateProductData(ProductRequestDTO requestDTO) {
        if (!isValidUnit(requestDTO.getUnit())) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_PRODUCT_INVALID_UNIT));
        }

        // Validar que el supplier existe si se proporciona
        if (requestDTO.getSupplierId() != null) {
            if (!supplierRepository.existsById(requestDTO.getSupplierId())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_SUPPLIER_NOT_FOUND));
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findHiddenProducts(String name, Pageable pageable) {
        Page<ProductResponseDTO> page;
        if (name != null && !name.isBlank()) {
            page = repository.findByNameContainingIgnoreCaseAndIsHiddenTrue(name, pageable)
                    .map(productMapper::toResponseDTO);
        } else {
            page = repository.findByIsHiddenTrue(pageable)
                    .map(productMapper::toResponseDTO);
        }
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @CacheEvict(value = { "products_page", "product" }, allEntries = true)
    @ProductAuditable(action = "TOGGLE_HIDDEN")
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleProductHiddenStatus(Integer id, boolean hidden) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[] { id })));

        product.setHidden(hidden);
        repository.save(product);
    }

    private boolean isValidUnit(String unit) {
        return unit != null && VALID_UNITS.contains(unit.toUpperCase());
    }

    @CacheEvict(value = { "products_page", "product" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class,
            Exception.class }, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProductResponseDTO> updateStockManually(Integer id, ProductRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    BigDecimal previousStock = existing.getCurrentStock();
                    BigDecimal newStock = requestDTO.getCurrentStock();
                    BigDecimal stockDelta = newStock.subtract(previousStock);

                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.existsByName(requestDTO.getName())) {
                        throw new InvalidOperationException(
                                i18nService.getMessage(MessageKey.ERROR_PRODUCT_ALREADY_EXISTS));
                    }
                    validateProductData(requestDTO);

                    existing.setName(requestDTO.getName());
                    existing.setType(requestDTO.getType());
                    existing.setUnit(requestDTO.getUnit());
                    existing.setUnitPrice(requestDTO.getUnitPrice());
                    existing.setProductCode(requestDTO.getProductCode());
                    if (requestDTO.getAvailabilityPercentage() != null) {
                        existing.setAvailabilityPercentage(requestDTO.getAvailabilityPercentage());
                    }
                    if (requestDTO.getMinimumStock() != null) {
                        existing.setMinimumStock(requestDTO.getMinimumStock());
                    }
                    if (requestDTO.getSupplierId() != null) {
                        existing.setSupplier(supplierRepository.findById(requestDTO.getSupplierId()).orElse(null));
                    }

                    if (stockDelta.compareTo(BigDecimal.ZERO) != 0) {
                        User currentUser = securityContextHelper.getCurrentUser();

                        stockLedgerService.recordStockMovement(
                                existing.getId(),
                                stockDelta,
                                MovementType.MODIFICACION,
                                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_MANUAL_ADJUSTMENT,
                                        new Object[] { existing.getName() }),
                                currentUser,
                                null);

                        Product updated = repository.findById(id).orElseThrow();
                        return productMapper.toResponseDTO(updated);
                    } else {

                        try {
                            return productMapper.toResponseDTO(repository.save(existing));
                        } catch (OptimisticLockingFailureException ex) {
                            throw new ConcurrencyException(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK),
                                    id);
                        }
                    }
                });
    }

    @Transactional(readOnly = true)
    public ProductStatsResponseDTO getProductStats() {
        return ProductStatsResponseDTO.builder()
                .totalProducts(repository.countTotalProducts())
                .totalInventoryValue(repository.calculateTotalInventoryValue())
                .averagePrice(repository.calculateAveragePrice())
                .build();
    }
}
