package com.economato.inventory.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.response.OrderAuditResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.OrderAuditMapper;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderAudit;
import com.economato.inventory.repository.OrderAuditRepository;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class OrderAuditService {

    private final OrderAuditRepository repository;
    private final OrderAuditMapper orderAuditMapper;

    public OrderAuditService(OrderAuditRepository repository, OrderAuditMapper orderAuditMapper) {
        this.repository = repository;
        this.orderAuditMapper = orderAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OrderAuditResponseDTO> findById(Integer id) {
        return repository.findById(id)
                .map(orderAuditMapper::toResponseDTO);
    }

    @Async
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void logOrderAction(Order order, String action, String details, String previousState, String newState) {
        OrderAudit audit = new OrderAudit();
        audit.setOrder(order);
        audit.setAction(action);
        audit.setDetails(details);
        audit.setPreviousState(previousState);
        audit.setNewState(newState);
        repository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByOrderId(Integer id) {
        return repository.findProjectedByOrderId(id).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByUserId(Integer id) {
        return repository.findProjectedByUsersId(id).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByAuditDateBetween(java.time.LocalDateTime start,
            java.time.LocalDateTime end) {
        return repository.findProjectedByAuditDateBetween(start, end).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

}
