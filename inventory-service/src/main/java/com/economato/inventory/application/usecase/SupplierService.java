package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.request.SupplierRequestDTO;
import com.economato.inventory.application.dto.response.SupplierResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.application.mapper.SupplierMapper;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;

import java.util.Optional;
import java.util.List;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class SupplierService {
    private final I18nService i18nService;

    private final SupplierRepository repository;
    private final ProductRepository productRepository;
    private final SupplierMapper supplierMapper;

    public SupplierService(I18nService i18nService, SupplierRepository repository, ProductRepository productRepository,
            SupplierMapper supplierMapper) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.supplierMapper = supplierMapper;
    }

    @Cacheable(value = "suppliers_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<SupplierResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable)
                .map(supplierMapper::toResponseDTO);
    }

    @Cacheable(value = "supplier", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<SupplierResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(supplierMapper::toResponseDTO);
    }

    @CacheEvict(value = { "suppliers_page", "supplier" }, allEntries = true)
    @RealtimeSync(entityType = "supplier", action = "CREATE",
            affectedDomains = {"supplier"})
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public SupplierResponseDTO save(SupplierRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_SUPPLIER_ALREADY_EXISTS));
        }
        Supplier supplier = supplierMapper.toEntity(requestDTO);
        return supplierMapper.toResponseDTO(repository.save(supplier));
    }

        @Caching(evict = {
            @CacheEvict(value = "supplier", key = "#id"),
            @CacheEvict(value = "suppliers_page", allEntries = true),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true),
            @CacheEvict(value = "product", allEntries = true)
        })
    @RealtimeSync(entityType = "supplier", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"supplier", "product"})
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<SupplierResponseDTO> update(Integer id, SupplierRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    if (!existing.getName().equals(requestDTO.getName()) &&
                            repository.existsByName(requestDTO.getName())) {
                        throw new InvalidOperationException(
                                i18nService.getMessage(MessageKey.ERROR_SUPPLIER_ALREADY_EXISTS));
                    }
                    supplierMapper.updateEntity(requestDTO, existing);
                    return supplierMapper.toResponseDTO(repository.save(existing));
                });
    }

        @CacheEvict(value = { "suppliers_page", "supplier" }, allEntries = true)
    @RealtimeSync(entityType = "supplier", action = "DELETE", idFromArg = 0,
            affectedDomains = {"supplier", "product"})
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        repository.findById(id).ifPresent(supplier -> {
            if (productRepository.existsBySupplierId(id)) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_SUPPLIER_DELETE_HAS_PRODUCTS));
            }
            repository.deleteById(id);
        });
    }

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> findByNameContaining(String namePart) {
        return repository.findProjectedByNameContainingIgnoreCase(namePart).stream()
                .map(supplierMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponseDTO> findByNameOrEmailOrPhoneContaining(String term, Pageable pageable) {
        String normalized = term == null ? "" : term.trim();

        return repository
                .findProjectedByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        normalized,
                        normalized,
                        normalized,
                        pageable)
                .map(supplierMapper::toResponseDTO);
    }
}
