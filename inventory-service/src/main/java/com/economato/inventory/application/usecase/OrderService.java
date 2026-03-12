package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.OrderAuditable;
import com.economato.inventory.application.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderRequestDTO;
import com.economato.inventory.application.dto.response.OrderFilterResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.application.dto.response.OrderTotalCostResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.OrderSpecifications;
import org.springframework.data.jpa.domain.Specification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {
        private final I18nService i18nService;

        private final OrderRepository repository;
        private final UserRepository userRepository;
        private final ProductRepository productRepository;
        private final SupplierRepository supplierRepository;
        private final OrderMapper orderMapper;
        private final StockLedgerService stockLedgerService;

        public OrderService(I18nService i18nService, OrderRepository repository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        SupplierRepository supplierRepository,
                        OrderMapper orderMapper,
                        StockLedgerService stockLedgerService) {
                this.i18nService = i18nService;
                this.repository = repository;
                this.userRepository = userRepository;
                this.productRepository = productRepository;
                this.supplierRepository = supplierRepository;
                this.orderMapper = orderMapper;
                this.stockLedgerService = stockLedgerService;
        }

        @Transactional(readOnly = true)
        public Page<OrderResponseDTO> findAll(Pageable pageable) {
                return repository.findAllProjectedBy(pageable)
                                .map(orderMapper::toResponseDTO);
        }

        @Cacheable(value = "order", key = "#id")
        @Transactional(readOnly = true)
        public Optional<OrderResponseDTO> findById(Integer id) {
                return repository.findProjectedById(id)
                                .map(orderMapper::toResponseDTO);
        }

        @CacheEvict(value = { "orders", "order" }, allEntries = true)
        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public OrderResponseDTO save(OrderRequestDTO requestDTO) {
                Order order = new Order();
                order.setOrderDate(LocalDateTime.now());
                order.setStatus(OrderStatus.CREATED);

                User user = userRepository.findById(requestDTO.getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_USER_NOT_FOUND)));
                order.setUser(user);

                if (requestDTO.getSupplierId() != null) {
                        Supplier supplier = supplierRepository.findById(requestDTO.getSupplierId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "El proveedor especificado no existe."));
                        order.setSupplier(supplier);
                }

                for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
                        Product product = productRepository.findById(detailDTO.getProductId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));

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
        @Retryable(includes = {
                        org.springframework.orm.ObjectOptimisticLockingFailureException.class }, maxRetries = 3, delay = 100, multiplier = 2)
        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public Optional<OrderResponseDTO> update(Integer id, OrderRequestDTO requestDTO) {
                return repository.findById(id)
                                .map(existing -> {
                                        User user = userRepository.findById(requestDTO.getUserId())
                                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                                        i18nService.getMessage(
                                                                                        MessageKey.ERROR_USER_NOT_FOUND)));
                                        existing.setUser(user);

                                        if (requestDTO.getSupplierId() != null) {
                                                Supplier supplier = supplierRepository.findById(requestDTO.getSupplierId())
                                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                                "El proveedor especificado no existe."));
                                                existing.setSupplier(supplier);
                                        } else {
                                                existing.setSupplier(null);
                                        }

                                        existing.getDetails().clear();

                                        repository.saveAndFlush(existing);

                                        for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
                                                Product product = productRepository.findById(detailDTO.getProductId())
                                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                                i18nService.getMessage(
                                                                                                MessageKey.ERROR_PRODUCT_NOT_FOUND)));

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
        public List<OrderResponseDTO> findByUser(UserResponseDTO user) {
                return repository.findProjectedByUserId(user.getId()).stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<OrderResponseDTO> findByStatus(OrderStatus status) {
                return repository.findProjectedByStatus(status, Pageable.unpaged()).getContent().stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<OrderResponseDTO> findByDateRange(LocalDateTime start, LocalDateTime end) {
                return repository.findProjectedByOrderDateBetween(start, end).stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public OrderFilterResponseDTO findFiltered(
                        LocalDateTime startDate,
                        LocalDateTime endDate,
                        Integer userId,
                        Integer supplierId,
                        Integer orderId) {
                if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                        throw new InvalidOperationException("La fecha de inicio no puede ser mayor que la fecha de fin.");
                }

                Specification<Order> spec = (root, query, cb) -> cb.conjunction();

                if (orderId != null) {
                        spec = spec.and(OrderSpecifications.hasOrderId(orderId));
                }
                if (userId != null) {
                        spec = spec.and(OrderSpecifications.hasUserId(userId));
                }
                if (supplierId != null) {
                        spec = spec.and(OrderSpecifications.hasSupplierId(supplierId));
                }
                if (startDate != null) {
                        spec = spec.and(OrderSpecifications.hasOrderDateAfter(startDate));
                }
                if (endDate != null) {
                        spec = spec.and(OrderSpecifications.hasOrderDateBefore(endDate));
                }

                List<OrderResponseDTO> orders = repository.findAll(spec).stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();

                java.math.BigDecimal totalCost = orders.stream()
                                .map(OrderResponseDTO::getTotalPrice)
                                .filter(java.util.Objects::nonNull)
                                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                return OrderFilterResponseDTO.builder()
                                .orders(orders)
                                .totalCost(totalCost)
                                .totalOrders(orders.size())
                                .build();
        }

        @Transactional(readOnly = true)
        public OrderTotalCostResponseDTO getTotalCostAllOrders() {
                java.math.BigDecimal totalCost = repository.getTotalCostAllOrders();
                long totalOrders = repository.count();

                return OrderTotalCostResponseDTO.builder()
                                .totalCost(totalCost)
                                .totalOrders(totalOrders)
                                .build();
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
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

                order.setStatus(OrderStatus.REVIEW);

                boolean isComplete = true;

        for (var receptionItem : receptionData.getItems()) {
                OrderDetail detail = order.getDetails().stream()
                                .filter(d -> d.getProduct().getId().equals(receptionItem.getProductId()))
                                .findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(
                                                                MessageKey.ERROR_ORDER_PRODUCT_NOT_FOUND)));

                if (receptionItem.getQuantityReceived().compareTo(detail.getQuantity()) < 0) {
                        isComplete = false;
                }
                detail.setQuantityReceived(receptionItem.getQuantityReceived());
        }

        order.setStatus(isComplete ? OrderStatus.CONFIRMED : OrderStatus.INCOMPLETE);

        log.info("Procesando recepción de orden {} con estado final {} - Registrando en ledger inmutable", order.getId(), order.getStatus());

        for (OrderDetail detail : order.getDetails()) {
                // If received quantity is greater than 0, register it in the ledger
                if (detail.getQuantityReceived() != null && detail.getQuantityReceived().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        Product product = productRepository.findByIdForUpdate(detail.getProduct().getId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        i18nService.getMessage(
                                                                        MessageKey.ERROR_PRODUCT_NOT_FOUND)));

                        stockLedgerService.recordStockMovement(
                                        product.getId(),
                                        detail.getQuantityReceived(),
                                        MovementType.ENTRADA,
                                          i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_RECEPTION,
                                                          new Object[] { order.getId(), product.getName() }),
                                        order.getUser(),
                                        order.getId());
                }
        }

        log.info("Orden {} procesada - movimientos registrados en ledger", order.getId());

                Order savedOrder = repository.save(order);
                return orderMapper.toResponseDTO(savedOrder);
        }

        @Transactional(readOnly = true)
        public List<OrderResponseDTO> findPendingReception() {
                return repository.findProjectedByStatus(OrderStatus.PENDING, Pageable.unpaged()).getContent().stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();
        }

        @OrderAuditable(action = "CAMBIO_ESTADO_ORDEN")
        @Retryable(includes = {
                        org.springframework.orm.ObjectOptimisticLockingFailureException.class }, maxRetries = 3, delay = 100, multiplier = 2)
        @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
        public Optional<OrderResponseDTO> updateStatus(Integer orderId, OrderStatus newStatus) {
                return repository.findById(orderId)
                                .map(order -> {
                                        order.setStatus(newStatus);
                                        Order updatedOrder = repository.save(order);
                                        return orderMapper.toResponseDTO(updatedOrder);
                                });
        }

        @Transactional(readOnly = true)
        public java.util.List<com.economato.inventory.application.dto.response.OrderDetailResponseDTO> getMissingItems(Integer orderId) {
                Order order = repository.findByIdWithDetails(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

                if (!OrderStatus.INCOMPLETE.equals(order.getStatus())) {
                        throw new InvalidOperationException("Solo las ordenes incompletas tienen items faltantes.");
                }

                return order.getDetails().stream()
                                .filter(detail -> {
                                        java.math.BigDecimal received = detail.getQuantityReceived() != null ? detail.getQuantityReceived() : java.math.BigDecimal.ZERO;
                                        return detail.getQuantity().compareTo(received) > 0;
                                })
                                .map(detail -> {
                                        com.economato.inventory.application.dto.response.OrderDetailResponseDTO dto = new com.economato.inventory.application.dto.response.OrderDetailResponseDTO();
                                        dto.setOrderId(order.getId());
                                        dto.setProductId(detail.getProduct().getId());
                                        dto.setProductName(detail.getProduct().getName());
                                        
                                        java.math.BigDecimal received = detail.getQuantityReceived() != null ? detail.getQuantityReceived() : java.math.BigDecimal.ZERO;
                                        dto.setQuantity(detail.getQuantity().subtract(received)); // Faltante
                                        dto.setQuantityReceived(java.math.BigDecimal.ZERO);
                                        
                                        return dto;
                                })
                                .toList();
        }
}
