package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.SupplierRequestDTO;
import com.economato.inventory.dto.response.SupplierResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.SupplierMapper;
import com.economato.inventory.model.Supplier;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.SupplierRepository;

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

    @Transactional(readOnly = true)
    public Page<SupplierResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable)
                .map(supplierMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(supplierMapper::toResponseDTO);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public SupplierResponseDTO save(SupplierRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_SUPPLIER_ALREADY_EXISTS));
        }
        Supplier supplier = supplierMapper.toEntity(requestDTO);
        return supplierMapper.toResponseDTO(repository.save(supplier));
    }

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
}
