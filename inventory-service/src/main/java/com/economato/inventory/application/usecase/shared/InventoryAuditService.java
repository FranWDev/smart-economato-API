package com.economato.inventory.application.usecase.shared;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.shared.response.InventoryMovementResponseDTO;
import com.economato.inventory.application.mapper.shared.InventoryMovementMapper;
import com.economato.inventory.domain.model.shared.InventoryAudit;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.InventoryAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.shared.InventoryAuditSpecifications;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class InventoryAuditService {

    private final InventoryAuditRepository repository;
    private final InventoryMovementMapper inventoryMovementMapper;

    public InventoryAuditService(
            InventoryAuditRepository repository,
            InventoryMovementMapper inventoryMovementMapper) {
        this.repository = repository;
        this.inventoryMovementMapper = inventoryMovementMapper;
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponseDTO> findAll(Pageable pageable) {
        Page<InventoryMovementResponseDTO> page = repository.findAllProjectedBy(pageable)
                .map(inventoryMovementMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Optional<InventoryMovementResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(inventoryMovementMapper::toResponseDTO);
    }

    @Async
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void saveAuditLog(InventoryAudit movement) {
        repository.save(movement);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponseDTO> findByMovementType(String type) {
        return repository.findProjectedByMovementType(type).stream()
                .map(inventoryMovementMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponseDTO> findByMovementDateBetween(LocalDateTime start,
            LocalDateTime end) {
        return repository.findProjectedByMovementDateBetween(start, end).stream()
                .map(inventoryMovementMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponseDTO> findFiltered(
            LocalDateTime start, LocalDateTime end, String type, String productName, Pageable pageable) {
        
        Specification<InventoryAudit> spec = (root, query, cb) -> cb.conjunction();
        
        if (start != null) {
            spec = spec.and(InventoryAuditSpecifications.hasMovementDateAfter(start));
        }
        if (end != null) {
            spec = spec.and(InventoryAuditSpecifications.hasMovementDateBefore(end));
        }
        if (type != null && !type.isBlank()) {
            spec = spec.and(InventoryAuditSpecifications.hasMovementType(type));
        }
        if (productName != null && !productName.isBlank()) {
            spec = spec.and(InventoryAuditSpecifications.productNameContains(productName));
        }

        Page<InventoryMovementResponseDTO> page = repository.findAll(spec, pageable)
                .map(inventoryMovementMapper::toResponseDTO);
                
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }
}
