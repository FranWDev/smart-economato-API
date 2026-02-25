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
                .map(this::toResponseDTO)
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
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByUserId(Integer id) {
        return repository.findProjectedByUsersId(id).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByAuditDateBetween(java.time.LocalDateTime start,
            java.time.LocalDateTime end) {
        return repository.findProjectedByAuditDateBetween(start, end).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    private OrderAuditResponseDTO toResponseDTO(
            com.economato.inventory.dto.projection.OrderAuditProjection projection) {
        OrderAuditResponseDTO dto = new OrderAuditResponseDTO();
        dto.setId(projection.getId());
        dto.setAction(projection.getAction());
        dto.setDetails(projection.getDetails());
        dto.setPreviousState(projection.getPreviousState());
        dto.setNewState(projection.getNewState());
        dto.setAuditDate(projection.getAuditDate());

        if (projection.getOrder() != null) {
            dto.setOrderId(projection.getOrder().getId());
        }

        if (projection.getUsers() != null) {
            dto.setUserId(projection.getUsers().getId());
            dto.setUserName(projection.getUsers().getName());
        }
        return dto;
    }

}
