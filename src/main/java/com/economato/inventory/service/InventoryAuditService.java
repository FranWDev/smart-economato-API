package com.economato.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.response.InventoryMovementResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.InventoryMovementMapper;
import com.economato.inventory.model.InventoryAudit;
import com.economato.inventory.repository.InventoryAuditRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
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
    public List<InventoryMovementResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(inventoryMovementMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<InventoryMovementResponseDTO> findById(Integer id) {
        return repository.findById(id)
                .map(inventoryMovementMapper::toResponseDTO);
    }

    @Async
    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
    public void saveAuditLog(InventoryAudit movement) {
        repository.save(movement);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponseDTO> findByMovementType(String type) {
        return repository.findByMovementTypeWithDetails(type).stream()
                .map(inventoryMovementMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponseDTO> findByMovementDateBetween(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return repository.findByMovementDateBetweenWithDetails(start, end).stream()
                .map(inventoryMovementMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
