package com.economato.inventory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.annotation.OrderAuditable;
import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.dto.request.OrderRequestDTO;
import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.OrderMapper;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.OrderDetail;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class OrderService {

    private final OrderRepository repository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final StockLedgerService stockLedgerService;

    public OrderService(OrderRepository repository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderMapper orderMapper,
            StockLedgerService stockLedgerService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.stockLedgerService = stockLedgerService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(orderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "order", key = "#id")
    @Transactional(readOnly = true)
    public Optional<OrderResponseDTO> findById(Integer id) {
        return repository.findByIdWithDetails(id)
                .map(orderMapper::toResponseDTO);
    }

    @CacheEvict(value = { "orders", "order" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public OrderResponseDTO save(OrderRequestDTO requestDTO) {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setStatus("CREATED");

        User user = userRepository.findById(requestDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        order.setUsers(user);

        for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
            Product product = productRepository.findById(detailDTO.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(detailDTO.getQuantity());
            order.getDetails().add(detail);
        }

        Order savedOrder = repository.save(order);
        return orderMapper.toResponseDTO(savedOrder);
    }

    /**
     * Actualiza una orden completa (usuario y detalles) con bloqueo optimista
     * 
     * Utiliza @Retryable para manejar conflictos de concurrencia con reintentos automáticos
     */
    @CacheEvict(value = { "orders", "order" }, allEntries = true)
    @Retryable(
        retryFor = {org.springframework.orm.ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<OrderResponseDTO> update(Integer id, OrderRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    User user = userRepository.findById(requestDTO.getUserId())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    existing.setUsers(user);

                    existing.getDetails().clear();

                    repository.saveAndFlush(existing);

                    for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
                        Product product = productRepository.findById(detailDTO.getProductId())
                                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

                        OrderDetail detail = new OrderDetail();
                        detail.setOrder(existing);
                        detail.setProduct(product);
                        detail.setQuantity(detailDTO.getQuantity());
                        existing.getDetails().add(detail);
                    }

                    return orderMapper.toResponseDTO(repository.save(existing));
                });
    }

    @CacheEvict(value = { "orders", "order" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findByUser(UserResponseDTO user2) {
        return repository.findByUserIdWithDetails(user2.getId()).stream()
                .map(orderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findByStatus(String status) {
        return repository.findByStatusWithDetails(status).stream()
                .map(orderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByOrderDateBetweenWithDetails(start, end).stream()
                .map(orderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Procesa la recepción de una orden, validando que no haya menores cantidades
     * y actualizando el inventario con las cantidades recibidas.
     * 
     * Utiliza Pessimistic Locking para garantizar consistencia en el stock.
     */
    @OrderAuditable(action = "RECEPCION_ORDEN")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class,
            Exception.class }, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderResponseDTO receiveOrder(OrderReceptionRequestDTO receptionData) {
        Order order = repository.findByIdWithDetails(receptionData.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        order.setStatus("IN_REVIEW");

        for (var receptionItem : receptionData.getItems()) {
            OrderDetail detail = order.getDetails().stream()
                    .filter(d -> d.getProduct().getId().equals(receptionItem.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado en la orden"));

            if (receptionItem.getQuantityReceived().compareTo(detail.getQuantity()) < 0) {
                throw new InvalidOperationException(
                        "No se puede recibir menos cantidad de la solicitada para " + detail.getProduct().getName() +
                                ". Solicitado: " + detail.getQuantity() + ", Recibido: "
                                + receptionItem.getQuantityReceived());
            }

            detail.setQuantityReceived(receptionItem.getQuantityReceived());
        }

        order.setStatus(receptionData.getStatus());

        if ("CONFIRMED".equals(receptionData.getStatus())) {
            log.info("Confirmando orden {} - Registrando en ledger inmutable", order.getId());
            
            for (OrderDetail detail : order.getDetails()) {
                Product product = productRepository.findByIdForUpdate(detail.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

                stockLedgerService.recordStockMovement(
                    product.getId(),
                    detail.getQuantityReceived(),
                    MovementType.ENTRADA,
                    String.format("Recepción de pedido #%d - %s", order.getId(), product.getName()),
                    order.getUsers(),
                    order.getId()
                );
            }
            
            log.info("Orden {} confirmada - {} movimientos registrados en ledger", 
                    order.getId(), order.getDetails().size());
        }

        Order savedOrder = repository.save(order);
        return orderMapper.toResponseDTO(savedOrder);
    }

    /**
     * Obtiene órdenes pendientes de recepción
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findPendingReception() {
        return repository.findByStatusWithDetails("PENDING").stream()
                .map(orderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Actualiza el estado de una orden con bloqueo optimista y reintentos
     * Estados permitidos: CREATED, PENDING, REVIEW, COMPLETED, INCOMPLETE
     * 
     * Utiliza @Retryable para manejar conflictos de concurrencia automáticamente
     * con hasta 3 intentos y backoff exponencial de 100ms
     */
    @OrderAuditable(action = "CAMBIO_ESTADO_ORDEN")
    @Retryable(
        retryFor = {org.springframework.orm.ObjectOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public Optional<OrderResponseDTO> updateStatus(Integer orderId, String newStatus) {
        return repository.findById(orderId)
                .map(order -> {
                    validateStatus(newStatus);
                    order.setStatus(newStatus);
                    Order updatedOrder = repository.save(order);
                    return orderMapper.toResponseDTO(updatedOrder);
                });
    }

    /**
     * Valida que el estado sea uno de los permitidos
     */
    private void validateStatus(String status) {
        List<String> validStatuses = List.of("CREATED", "PENDING", "REVIEW", "COMPLETED", "INCOMPLETE");
        if (!validStatuses.contains(status)) {
            throw new InvalidOperationException("Estado de orden inválido: " + status);
        }
    }
}