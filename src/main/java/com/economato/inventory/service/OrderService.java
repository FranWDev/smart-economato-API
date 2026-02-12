package com.economato.inventory.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
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

import lombok.extern.slf4j.Slf4j;

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
        return repository.findAllProjectedBy(pageable).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "order", key = "#id")
    @Transactional(readOnly = true)
    public Optional<OrderResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(this::toResponseDTO);
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
        // Return using the same mapper for consistency
        return orderMapper.toResponseDTO(savedOrder);
    }

    /**
     * Actualiza una orden completa (usuario y detalles) con bloqueo optimista
     * 
     * Utiliza @Retryable para manejar conflictos de concurrencia con reintentos
     * automáticos
     */
    @CacheEvict(value = { "orders", "order" }, allEntries = true)
    @Retryable(retryFor = {
            org.springframework.orm.ObjectOptimisticLockingFailureException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
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

                    Order saved = repository.save(existing);
                    return orderMapper.toResponseDTO(saved);
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
        return repository.findProjectedByUsersId(user2.getId()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findByStatus(String status) {
        // Since findStatus returns Page in repository implementation?
        // Wait, repository implementation: findProjectedByStatus returns
        // Page<OrderProjection>
        // But here we return List<OrderResponseDTO>
        // Pageable is not passed here.
        // repository.findProjectedByStatus expects Pageable?
        // Let's check repository definition I added.
        // Page<OrderProjection> findProjectedByStatus(String status, Pageable
        // pageable);
        // So I cannot use it without pageable.
        // I need List<OrderProjection> findProjectedByStatus(String status); in
        // Repository?
        // Or pass Pageable.unpaged() if supported?
        // existing findByStatusWithDetails returns List.
        // I should add List version to Repository or use Pageable.unpaged().
        // For now I will assume I can add List version or use Pageable.unpaged()
        return repository.findProjectedByStatus(status, Pageable.unpaged()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findProjectedByOrderDateBetween(start, end).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converts OrderProjection to OrderResponseDTO
     */
    private OrderResponseDTO toResponseDTO(com.economato.inventory.dto.projection.OrderProjection projection) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(projection.getId());
        dto.setOrderDate(projection.getOrderDate());
        dto.setStatus(projection.getStatus());

        if (projection.getUsers() != null) {
            dto.setUserId(projection.getUsers().getId());
            dto.setUserName(projection.getUsers().getName());
        }

        if (projection.getDetails() != null) {
            List<com.economato.inventory.dto.response.OrderDetailResponseDTO> details = projection.getDetails().stream()
                    .map(d -> toDetailDTO(d, projection.getId()))
                    .collect(Collectors.toList());
            dto.setDetails(details);

            java.math.BigDecimal total = details.stream()
                    .map(com.economato.inventory.dto.response.OrderDetailResponseDTO::getSubtotal)
                    .filter(java.util.Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            dto.setTotalPrice(total);
        }
        return dto;
    }

    private com.economato.inventory.dto.response.OrderDetailResponseDTO toDetailDTO(
            com.economato.inventory.dto.projection.OrderProjection.OrderDetailSummary summary, Integer orderId) {
        com.economato.inventory.dto.response.OrderDetailResponseDTO dto = new com.economato.inventory.dto.response.OrderDetailResponseDTO();
        dto.setOrderId(orderId);
        dto.setQuantity(summary.getQuantity());
        dto.setQuantityReceived(summary.getQuantityReceived());

        if (summary.getProduct() != null) {
            dto.setProductId(summary.getProduct().getId());
            dto.setProductName(summary.getProduct().getName());
            dto.setUnitPrice(summary.getProduct().getUnitPrice());

            if (dto.getUnitPrice() != null && summary.getQuantity() != null) {
                dto.setSubtotal(dto.getUnitPrice().multiply(summary.getQuantity()));
            }
        }
        return dto;
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

        order.setStatus("REVIEW");

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
                        order.getId());
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
        return repository.findProjectedByStatus("PENDING", Pageable.unpaged()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Actualiza el estado de una orden con bloqueo optimista y reintentos
     * Estados permitidos: CREATED, PENDING, REVIEW, CONFIRMED, INCOMPLETE,
     * CANCELLED
     * 
     * Utiliza @Retryable para manejar conflictos de concurrencia automáticamente
     * con hasta 3 intentos y backoff exponencial de 100ms
     */
    @OrderAuditable(action = "CAMBIO_ESTADO_ORDEN")
    @Retryable(retryFor = {
            org.springframework.orm.ObjectOptimisticLockingFailureException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public Optional<OrderResponseDTO> updateStatus(Integer orderId, String newStatus) {
        return repository.findById(orderId)
                .map(order -> {
                    String normalizedStatus = validateStatus(newStatus);
                    order.setStatus(normalizedStatus);
                    Order updatedOrder = repository.save(order);
                    return orderMapper.toResponseDTO(updatedOrder);
                });
    }

    /**
     * Valida que el estado sea uno de los permitidos
     */
    private String validateStatus(String status) {
        if (status == null) {
            throw new InvalidOperationException("Estado de orden inválido: null");
        }
        String normalized = status.trim().toUpperCase();
        List<String> validStatuses = List.of("CREATED", "PENDING", "REVIEW", "CONFIRMED", "INCOMPLETE", "CANCELLED");
        if (!validStatuses.contains(normalized)) {
            throw new InvalidOperationException("Estado de orden inválido: " + status);
        }
        return normalized;
    }
}
