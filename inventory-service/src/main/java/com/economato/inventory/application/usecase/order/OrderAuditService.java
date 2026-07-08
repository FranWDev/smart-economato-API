package com.economato.inventory.application.usecase.order;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.order.response.OrderAuditResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.application.mapper.order.OrderAuditMapper;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderAudit;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderAuditRepository;

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
    public Page<OrderAuditResponseDTO> findAll(Pageable pageable) {
        Page<OrderAuditResponseDTO> page = repository.findAllProjectedBy(pageable)
                .map(orderAuditMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
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
        return repository.findProjectedByUserId(id).stream()
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
