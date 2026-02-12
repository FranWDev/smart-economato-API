package com.economato.inventory.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.projection.SupplierProjection;
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
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class SupplierService {

    private final SupplierRepository repository;
    private final ProductRepository productRepository;
    private final SupplierMapper supplierMapper;

    public SupplierService(SupplierRepository repository, ProductRepository productRepository,
            SupplierMapper supplierMapper) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.supplierMapper = supplierMapper;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable)
                .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(this::toResponseDTO);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public SupplierResponseDTO save(SupplierRequestDTO requestDTO) {
        if (repository.existsByName(requestDTO.getName())) {
            throw new InvalidOperationException("Ya existe un proveedor con ese nombre");
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
                        throw new InvalidOperationException("Ya existe un proveedor con ese nombre");
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
                        "No se puede eliminar el proveedor porque tiene productos asociados");
            }
            repository.deleteById(id);
        });
    }

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> findByNameContaining(String namePart) {
        return repository.findProjectedByNameContainingIgnoreCase(namePart).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convierte una proyección de Supplier a SupplierResponseDTO.
     */
    private SupplierResponseDTO toResponseDTO(SupplierProjection projection) {
        return new SupplierResponseDTO(projection.getId(), projection.getName());
    }
}
