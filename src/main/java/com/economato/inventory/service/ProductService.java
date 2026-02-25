package com.economato.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.annotation.ProductAuditable;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.exception.ConcurrencyException;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.mapper.ProductMapper;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.InventoryAuditRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeComponentRepository;
import com.economato.inventory.repository.SupplierRepository;
import com.economato.inventory.repository.UserRepository;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
public class ProductService {

    private static final Set<String> VALID_UNITS = Set.of(
            "KG", "G", "L", "ML", "UNIDAD", "MANOJO", "BOTE",
            "PAQUETE", "SOBRE", "HOJA", "TUBO", "UDS", "UND");

    private final ProductRepository repository;
    private final InventoryAuditRepository movementRepository;
    private final RecipeComponentRepository recipeComponentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;
    private final StockLedgerService stockLedgerService;
    private final UserRepository userRepository;

    public ProductService(
            ProductRepository repository,
            InventoryAuditRepository movementRepository,
            RecipeComponentRepository recipeComponentRepository,
            SupplierRepository supplierRepository,
            ProductMapper productMapper,
            StockLedgerService stockLedgerService,
            UserRepository userRepository) {
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.recipeComponentRepository = recipeComponentRepository;
        this.supplierRepository = supplierRepository;
        this.productMapper = productMapper;
        this.stockLedgerService = stockLedgerService;
        this.userRepository = userRepository;
    }

    @Cacheable(value = "products_page_v4", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(Pageable pageable) {
        Page<ProductResponseDTO> page = repository.findAllProjectedBy(pageable)
                .map(productMapper::toResponseDTO);
        return new com.economato.inventory.dto.RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "product_v2", key = "#id")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "product_v2", key = "'code:' + #codebar")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findByCodebar(String codebar) {
        return repository.findProjectedByProductCode(codebar)
                .map(productMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findByName(String namePart, Pageable pageable) {
        return repository.findProjectedByNameContainingIgnoreCase(namePart, pageable)
                .map(productMapper::toResponseDTO);
    }

    @CacheEvict(value = { "products_page_v2", "product_v2" }, allEntries = true)
    @ProductAuditable(action = "CREATE_PRODUCT")
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public ProductResponseDTO save(ProductRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException("Ya existe un producto con ese nombre");
        }

        validateProductData(requestDTO);
        Product product = productMapper.toEntity(requestDTO);
        return productMapper.toResponseDTO(repository.save(product));
    }

    @CacheEvict(value = { "products_page_v2", "product_v2" }, allEntries = true)
    @ProductAuditable(action = "UPDATE_PRODUCT")
    @Retryable(retryFor = { OptimisticLockingFailureException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class,
            Exception.class }, isolation = Isolation.REPEATABLE_READ)
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

    @CacheEvict(value = { "products_page_v2", "product_v2" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
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
        return repository.findProjectedByType(type).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByNameContaining(String namePart) {
        return repository.findProjectedByNameContainingIgnoreCase(namePart).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByStockLessThan(BigDecimal stock) {
        return repository.findProjectedByCurrentStockLessThan(stock).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByPriceRange(BigDecimal min, BigDecimal max) {
        return repository.findProjectedByUnitPriceBetween(min, max).stream()
                .map(productMapper::toResponseDTO)
                .toList();
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
        return unit != null && VALID_UNITS.contains(unit.toUpperCase());
    }

    @CacheEvict(value = { "products_page_v2", "product_v2" }, allEntries = true)
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
                        throw new InvalidOperationException("Ya existe un producto con ese nombre");
                    }
                    validateProductData(requestDTO);

                    existing.setName(requestDTO.getName());
                    existing.setType(requestDTO.getType());
                    existing.setUnit(requestDTO.getUnit());
                    existing.setUnitPrice(requestDTO.getUnitPrice());
                    existing.setProductCode(requestDTO.getProductCode());
                    if (requestDTO.getSupplierId() != null) {
                        existing.setSupplier(supplierRepository.findById(requestDTO.getSupplierId()).orElse(null));
                    }

                    if (stockDelta.compareTo(BigDecimal.ZERO) != 0) {
                        User currentUser = getCurrentUser();

                        stockLedgerService.recordStockMovement(
                                existing.getId(),
                                stockDelta,
                                MovementType.AJUSTE,
                                String.format("Modificación manual del stock de %s", existing.getName()),
                                currentUser,
                                null);

                        Product updated = repository.findById(id).orElseThrow();
                        return productMapper.toResponseDTO(updated);
                    } else {

                        try {
                            return productMapper.toResponseDTO(repository.save(existing));
                        } catch (OptimisticLockingFailureException ex) {
                            throw new ConcurrencyException("Product", id);
                        }
                    }
                });
    }

    private User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();
                return userRepository.findByName(username).orElse(null);
            }
        } catch (Exception e) {
            // Log silencioso - continuar sin usuario
        }
        return null;
    }
}