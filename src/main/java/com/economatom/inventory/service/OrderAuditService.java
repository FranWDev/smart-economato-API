package com.economatom.inventory.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economatom.inventory.dto.response.OrderAuditResponseDTO;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.exception.ResourceNotFoundException;
import com.economatom.inventory.mapper.OrderAuditMapper;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.OrderAudit;
import com.economatom.inventory.repository.OrderAuditRepository;
import com.economatom.inventory.repository.UserRepository;

@Service
@Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
public class OrderAuditService {

    private final OrderAuditRepository repository;
    private final UserRepository userRepository;
    private final OrderAuditMapper orderAuditMapper;

    public OrderAuditService(OrderAuditRepository repository, UserRepository userRepository, OrderAuditMapper orderAuditMapper) {
            this.repository = repository;
            this.userRepository = userRepository;
            this.orderAuditMapper = orderAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OrderAuditResponseDTO> findById(Integer id) {
        return repository.findById(id)
                .map(orderAuditMapper::toResponseDTO);
    }

    @Async
    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
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
        return repository.findByOrderIdWithDetails(id).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByUserId(Integer id) {
        return repository.findByUsersId(userRepository.findById(id).get().getId()).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderAuditResponseDTO> findByAuditDateBetween(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return repository.findByAuditDateBetweenWithDetails(start, end).stream()
                .map(orderAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

}
