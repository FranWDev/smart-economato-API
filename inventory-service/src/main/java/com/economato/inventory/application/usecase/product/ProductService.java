package com.economato.inventory.application.usecase.product;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.domain.model.product.ValidUnit;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.product.request.ProductRequestDTO;
import com.economato.inventory.application.dto.product.response.ProductResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductStatsResponseDTO;
import com.economato.inventory.application.mapper.product.ProductMapper;
import com.economato.inventory.domain.product.ProductAuditable;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ConcurrencyException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.InventoryAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeComponentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.economato.inventory.infrastructure.adapter.out.external.product.reports.ProductExcelService;
import com.economato.inventory.infrastructure.adapter.out.external.ledger.reports.StockLedgerPdfService;
import com.economato.inventory.application.dto.ledger.response.LedgerPdfResponseDTO;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
@RequiredArgsConstructor
public class ProductService {
    private final I18nService i18nService;

    private static final Set<String> VALID_UNITS = Set.of(
            // Peso
            "KG", "G", "MG", "ONZA", "LIBRA",
            // Volumen
            "L", "ML", "CL", "DL", "GARRAFA",
            // Medidas de cocina
            "CUCHARADA", "CUCHARADITA", "TAZA", "PIZCA", "VASO",
            // Unidades discretas
            "UNIDAD", "UND", "UDS", "PIEZA", "DOCENA",
            // Envases/Empaquetado
            "BOTE", "LATA", "PAQUETE", "SOBRE", "BOLSA", "CAJA", "SACO", "BANDEJA", "TUBO",
            // Formas específicas de cocina
            "MANOJO", "HOJA", "LONCHA", "DIENTE", "RAMA", "FILETE", "RODAJA", "REBANADA"
    );

    private final ProductRepository repository;
    private final InventoryAuditRepository movementRepository;
    private final RecipeComponentRepository recipeComponentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;
    private final StockLedgerService stockLedgerService;
    private final ProductBatchService productBatchService;
    private final SecurityContextHelper securityContextHelper;
    private final RecipeService recipeService;
    private final ValidUnitService validUnitService;
    private final ProductSkuGuard productSkuGuard;
    private final ProductExcelService productExcelService;
    private final StockLedgerPdfService stockLedgerPdfService;

    @lombok.Getter
    @lombok.Builder
    public static class StockLedgerPdfDownloadDTO {
        private final byte[] pdfContent;
        private final String filename;
        private final boolean integrityValid;
        private final String integrityMessage;
        private final String integrityError;
    }

    public StreamingResponseBody getProductsExcelStream() {
        return out -> productExcelService.streamProductsExcel(out);
    }

    @Transactional(readOnly = true)
    public Optional<StockLedgerPdfDownloadDTO> getStockLedgerPdfDownload(Integer id) {
        return repository.findById(id)
                .map(product -> {
                    LedgerPdfResponseDTO pdfResponse = stockLedgerPdfService.generateStockLedgerPdfWithIntegrity(id);
                    
                    String integrityError = null;
                    if (!pdfResponse.isIntegrityValid() && pdfResponse.getIntegrityErrors() != null) {
                        integrityError = pdfResponse.getIntegrityErrors().isEmpty() 
                                ? i18nService.getMessage(MessageKey.ERROR_INTERNAL_SERVER_ERROR) 
                                : pdfResponse.getIntegrityErrors().get(0);
                    }

                    return StockLedgerPdfDownloadDTO.builder()
                            .pdfContent(pdfResponse.getPdfContent())
                            .filename("ledger_stock_" + sanitizeFilename(product.getName()) + ".pdf")
                            .integrityValid(pdfResponse.isIntegrityValid())
                            .integrityMessage(pdfResponse.getIntegrityMessage())
                            .integrityError(integrityError)
                            .build();
                });
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "producto";
        }
        String cleaned = filename.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s-]", "_")
                .replaceAll("\\s", "_");
        if (cleaned.isBlank()) {
            return "producto";
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 50));
    }

    @Cacheable(value = "products_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(Pageable pageable) {
        Page<ProductResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(productMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "product", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "product", key = "'code:' + #codebar", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<ProductResponseDTO> findByCodebar(String codebar) {
        return repository.findProjectedByProductCode(codebar)
                .map(productMapper::toResponseDTO);
    }

    @Cacheable(value = "products_search", key = "'name:' + (#namePart != null ? #namePart.toLowerCase() : '') + ':' + #pageable.pageNumber + '-' + #pageable.pageSize")
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

        @Caching(
            put = {
                @CachePut(value = "product", key = "#result.id")
            },
            evict = {
                @CacheEvict(value = "products_page", allEntries = true),
                @CacheEvict(value = "product_stats", allEntries = true),
                @CacheEvict(value = "products_search", allEntries = true)
            })
    @RealtimeSync(entityType = "product", action = "CREATE",
            affectedDomains = {"product"})
    @ProductAuditable(action = "CREATE_PRODUCT")
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public ProductResponseDTO save(ProductRequestDTO requestDTO) {
        productSkuGuard.validateNameUniqueness(requestDTO.getName());
        productSkuGuard.validateProductCodeUniqueness(requestDTO.getProductCode());
        productSkuGuard.validateProductData(requestDTO);


        BigDecimal initialStock = requestDTO.getCurrentStock();
        // Forzamos 0 stock inicial para que sea el ledger quien lo establezca y cree los lotes necesarios
        requestDTO.setCurrentStock(BigDecimal.ZERO);

        Product product = repository.saveAndFlush(productMapper.toEntity(requestDTO));

        if (initialStock != null && initialStock.compareTo(BigDecimal.ZERO) > 0) {
            if (requestDTO.getExpirationDate() == null) {
                throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
            }
            User currentUser = securityContextHelper.getCurrentUser();
            stockLedgerService.recordStockMovement(
                    product.getId(),
                    initialStock,
                    MovementType.ENTRADA,
                    i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_INITIAL_STOCK, new Object[]{product.getName()}),
                    currentUser,
                    null,
                    requestDTO.getExpirationDate());

            // Recargamos el producto para obtener el stock actualizado por el ledger
            product = repository.findByIdWithSupplier(product.getId()).orElse(product);
        }

        return productMapper.toResponseDTO(product);
    }

        @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true)
        })
    @RealtimeSync(entityType = "product", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"product", "recipe", "weekly_plan"})
    @ProductAuditable(action = "UPDATE_PRODUCT")
    @Retryable(includes = { OptimisticLockingFailureException.class }, maxRetries = 3, delay = 100)
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class,
            Exception.class }, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProductResponseDTO> update(Integer id, ProductRequestDTO requestDTO) {
        return repository.findByIdWithSupplier(id)
                .map(existing -> {
                    if (!existing.getName().equals(requestDTO.getName())) {
                        productSkuGuard.validateNameUniqueness(requestDTO.getName());
                    }

                    if (requestDTO.getProductCode() != null &&
                            !requestDTO.getProductCode().equals(existing.getProductCode())) {
                        productSkuGuard.validateProductCodeUniqueness(requestDTO.getProductCode());
                    }

                    productSkuGuard.validateProductData(requestDTO);
                    
                    BigDecimal oldPrice = existing.getUnitPrice();
                    BigDecimal oldAvailability = existing.getAvailabilityPercentage();
                    
                    productMapper.updateEntity(requestDTO, existing);

                    try {
                        Product saved = repository.save(existing);
                        
                        boolean priceChanged = (oldPrice == null && saved.getUnitPrice() != null) || 
                                             (oldPrice != null && oldPrice.compareTo(saved.getUnitPrice()) != 0);
                        boolean availabilityChanged = (oldAvailability == null && saved.getAvailabilityPercentage() != null) || 
                                                   (oldAvailability != null && oldAvailability.compareTo(saved.getAvailabilityPercentage()) != 0);
                        
                        if (priceChanged || availabilityChanged) {
                            recipeService.recalculateRecipesUsingProduct(id);
                        }
                        
                        return productMapper.toResponseDTO(saved);
                    } catch (OptimisticLockingFailureException ex) {
                        throw new ConcurrencyException(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK), id);
                    }
                });
    }

        @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_stats", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true)
        })
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


    @Cacheable(value = "products_search", key = "'containing:' + (#namePart != null ? #namePart.toLowerCase() : '')")
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByNameContaining(String namePart) {
        return repository.findByNameContainingIgnoreCaseAndIsHiddenFalse(namePart).stream()
                .map(productMapper::toResponseDTO)
                .toList();
    }


    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByPriceRange(BigDecimal min, BigDecimal max) {
        return repository.findByUnitPriceBetweenAndIsHiddenFalse(min, max).stream()
                .map(productMapper::toResponseDTO)
                .toList();
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

        @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true)
        })
    @RealtimeSync(entityType = "product", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"product", "recipe", "weekly_plan"})
    @ProductAuditable(action = "TOGGLE_HIDDEN")
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleProductHiddenStatus(Integer id, boolean hidden) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[] { id })));

        product.setHidden(hidden);
        repository.save(product);
    }



    /* Por que se limpia TODA la caché?
     * Porque, al usarse normalmente con páginas, alterar un producto provoca que se altere su posición en la página.
     */
        @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_stats", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true)
        })
    @RealtimeSync(entityType = "product", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"product", "ledger", "weekly_plan", "stock_alerts", "recipe"})
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class,
            Exception.class }, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProductResponseDTO> updateStockManually(Integer id, ProductRequestDTO requestDTO) {
        return repository.findByIdWithSupplier(id)
                .map(existing -> {
                    BigDecimal previousStock = existing.getCurrentStock();
                    BigDecimal newStock = requestDTO.getCurrentStock();
                    BigDecimal stockDelta = newStock.subtract(previousStock);

                    if (!existing.getName().equals(requestDTO.getName())) {
                        productSkuGuard.validateNameUniqueness(requestDTO.getName());
                    }
                    productSkuGuard.validateProductData(requestDTO);


                    existing.setName(requestDTO.getName());
                    existing.setUnit(requestDTO.getUnit());
                    existing.setUnitPrice(requestDTO.getUnitPrice());
                    existing.setProductCode(requestDTO.getProductCode());
                    existing.setLotQuantity(requestDTO.getLotQuantity());
                    if (requestDTO.getAvailabilityPercentage() != null) {
                        existing.setAvailabilityPercentage(requestDTO.getAvailabilityPercentage());
                    }
                    if (requestDTO.getSupplierId() != null) {
                        existing.setSupplier(supplierRepository.findById(requestDTO.getSupplierId()).orElse(null));
                    }

                    if (stockDelta.compareTo(BigDecimal.ZERO) != 0) {
                        User currentUser = securityContextHelper.getCurrentUser();

                        stockLedgerService.recordManualAdjustment(
                                existing.getId(),
                                stockDelta,
                                MovementType.MODIFICACION,
                                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_MANUAL_ADJUSTMENT,
                                        new Object[] { existing.getName() }),
                                currentUser,
                                requestDTO.getBatchId(),
                                requestDTO.getExpirationDate());

                        Product updated = repository.findByIdWithSupplier(id).orElseThrow();
                        
                        // En ajuste manual de stock también permitimos cambiar precio/merma
                        recipeService.recalculateRecipesUsingProduct(id);
                        
                        return productMapper.toResponseDTO(updated);
                    } else {

                        try {
                            Product saved = repository.save(existing);
                            recipeService.recalculateRecipesUsingProduct(id);
                            return productMapper.toResponseDTO(saved);
                        } catch (OptimisticLockingFailureException ex) {
                            throw new ConcurrencyException(i18nService.getMessage(MessageKey.ERROR_OPTIMISTIC_LOCK),
                                    id);
                        }
                    }
                });
    }

    @Cacheable(value = "product_stats", key = "'global'")
    @Transactional(readOnly = true)
    public ProductStatsResponseDTO getProductStats() {
        return ProductStatsResponseDTO.builder()
                .totalProducts(repository.countTotalProducts())
                .totalInventoryValue(repository.calculateTotalInventoryValue())
                .averagePrice(repository.calculateAveragePrice())
                .build();
    }
}