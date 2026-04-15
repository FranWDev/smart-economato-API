package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.request.LotReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderRequestDTO;
import com.economato.inventory.application.dto.response.LotReceptionResponseDTO;
import com.economato.inventory.application.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.application.dto.response.OrderFilterResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.application.dto.response.OrderTotalCostResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.domain.OrderAuditable;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.OrderSpecifications;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

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
        private final ProductBatchService productBatchService;
        private final Environment environment;

        public OrderService(I18nService i18nService, OrderRepository repository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        SupplierRepository supplierRepository,
                        OrderMapper orderMapper,
                        StockLedgerService stockLedgerService,
                        ProductBatchService productBatchService,
                        Environment environment) {
                this.i18nService = i18nService;
                this.repository = repository;
                this.userRepository = userRepository;
                this.productRepository = productRepository;
                this.supplierRepository = supplierRepository;
                this.orderMapper = orderMapper;
                this.stockLedgerService = stockLedgerService;
                this.productBatchService = productBatchService;
                this.environment = environment;
        }

        @Cacheable(value = "orders_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
        @Transactional(readOnly = true)
        public Page<OrderResponseDTO> findAll(Pageable pageable) {
                return repository.findAllProjectedBy(pageable)
                                .map(orderMapper::toResponseDTO);
        }

        @Cacheable(value = "order", key = "#id", unless = "#result == null")
        @Transactional(readOnly = true)
        public Optional<OrderResponseDTO> findById(Integer id) {
                return repository.findProjectedById(id)
                                .map(orderMapper::toResponseDTO);
        }

        @Caching(evict = {
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true)
        })
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

                List<Integer> productIds = requestDTO.getDetails().stream()
                                .map(OrderDetailRequestDTO::getProductId)
                                .toList();
                Map<Integer, Product> productsById = productRepository.findAllById(productIds).stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));
                if (productsById.size() != new HashSet<>(productIds).size()) {
                        throw new ResourceNotFoundException(
                                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND));
                }

                for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
                        Product product = productsById.get(detailDTO.getProductId());
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
         * Utiliza @Retryable para manejar conflictos de concurrencia con reintentos
         * automáticos
         */
        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#id"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true)
        })
        @Retryable(includes = {
                        ObjectOptimisticLockingFailureException.class }, maxRetries = 3, delay = 100, multiplier = 2)
        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class, Exception.class })
        public Optional<OrderResponseDTO> update(Integer id, OrderRequestDTO requestDTO) {
                return repository.findByIdWithDetails(id)
                                .map(existing -> {
                                        User user = userRepository.findById(requestDTO.getUserId())
                                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                                        i18nService.getMessage(
                                                                                        MessageKey.ERROR_USER_NOT_FOUND)));
                                        existing.setUser(user);

                                        if (requestDTO.getSupplierId() != null) {
                                                Supplier supplier = supplierRepository
                                                                .findById(requestDTO.getSupplierId())
                                                                .orElseThrow(() -> new ResourceNotFoundException(
                                                                                "El proveedor especificado no existe."));
                                                existing.setSupplier(supplier);
                                        } else {
                                                existing.setSupplier(null);
                                        }

                                        existing.getDetails().clear();

                                        repository.saveAndFlush(existing);

                                        List<Integer> productIds = requestDTO.getDetails().stream()
                                                        .map(OrderDetailRequestDTO::getProductId)
                                                        .toList();
                                        Map<Integer, Product> productsById = productRepository.findAllById(productIds)
                                                        .stream()
                                                        .collect(Collectors.toMap(Product::getId, p -> p));
                                        if (productsById.size() != new HashSet<>(productIds).size()) {
                                                throw new ResourceNotFoundException(
                                                                i18nService.getMessage(
                                                                                MessageKey.ERROR_PRODUCT_NOT_FOUND));
                                        }

                                        for (OrderDetailRequestDTO detailDTO : requestDTO.getDetails()) {
                                                Product product = productsById.get(detailDTO.getProductId());

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

        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#id"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true)
        })
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
                        throw new InvalidOperationException(
                                        "La fecha de inicio no puede ser mayor que la fecha de fin.");
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

                BigDecimal totalCost = orders.stream()
                                .map(OrderResponseDTO::getTotalPrice)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return OrderFilterResponseDTO.builder()
                                .orders(orders)
                                .totalCost(totalCost)
                                .totalOrders(orders.size())
                                .build();
        }

        @Cacheable(value = "order_stats", key = "'totalCost'")
        @Transactional(readOnly = true)
        public OrderTotalCostResponseDTO getTotalCostAllOrders() {
                BigDecimal totalCost = repository.getTotalCostAllOrders();
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
        @PredictorTrigger(action = "ORDER_RECEPTION")
        @OrderAuditable(action = "RECEPCION_ORDEN")
        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#receptionData.orderId"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true)
        })
        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class,
                        Exception.class }, isolation = Isolation.REPEATABLE_READ)
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

                        BigDecimal lotsSum = receptionItem.getLots().stream()
                                        .map(LotReceptionRequestDTO::getQuantity)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (lotsSum.compareTo(receptionItem.getQuantityReceived()) != 0) {
                                throw new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_LOTS_SUM_MISMATCH));
                        }

                        if (receptionItem.getQuantityReceived().compareTo(detail.getQuantity()) < 0) {
                                isComplete = false;
                        }
                        detail.setQuantityReceived(receptionItem.getQuantityReceived());
                }

                order.setStatus(isComplete ? OrderStatus.CONFIRMED : OrderStatus.INCOMPLETE);

                log.info("Procesando recepción de orden {} con estado final {} - Registrando en ledger inmutable",
                                order.getId(), order.getStatus());

                Map<Integer, OrderReceptionDetailRequestDTO> receptionByProductId = new HashMap<>();
                for (var item : receptionData.getItems()) {
                        receptionByProductId.put(item.getProductId(), item);
                }

                List<Integer> productIdsToLock = order.getDetails().stream()
                                .filter(d -> d.getQuantityReceived() != null
                                                && d.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0)
                                .map(d -> d.getProduct().getId())
                                .toList();

                Map<Integer, Product> productsById;
                boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");

                if (productIdsToLock.isEmpty()) {
                        productsById = Collections.emptyMap();
                } else if (isTestProfile) {
                        productsById = productRepository.findAllById(productIdsToLock).stream()
                                        .collect(Collectors.toMap(Product::getId, p -> p));
                } else {
                        productsById = productRepository.findByIdsForUpdate(productIdsToLock).stream()
                                        .collect(Collectors.toMap(Product::getId, p -> p));
                }

                for (OrderDetail detail : order.getDetails()) {
                        if (detail.getQuantityReceived() != null
                                        && detail.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0) {
                                Product product = productsById.get(detail.getProduct().getId());
                                if (product == null) {
                                        throw new ResourceNotFoundException(
                                                        i18nService.getMessage(
                                                                        MessageKey.ERROR_PRODUCT_NOT_FOUND));
                                }

                                var receptionItem = receptionByProductId.get(detail.getProduct().getId());
                                if (receptionItem != null && receptionItem.getLots() != null) {
                                        for (var lot : receptionItem.getLots()) {
                                                stockLedgerService.recordStockMovement(
                                                                product.getId(),
                                                                lot.getQuantity(),
                                                                MovementType.ENTRADA,
                                                                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_RECEPTION,
                                                                                new Object[] { order.getId(), product.getName() }),
                                                                order.getUser(),
                                                                order.getId(),
                                                                lot.getExpirationDate(),
                                                                null,
                                                                lot.getBatchCode());
                                        }
                                }
                        }
                }

                log.info("Orden {} procesada - movimientos registrados en ledger", order.getId());

                Order savedOrder = repository.save(order);
                OrderResponseDTO responseDTO = orderMapper.toResponseDTO(savedOrder);

                if (responseDTO.getDetails() != null) {
                        for (OrderDetailResponseDTO detailResp : responseDTO.getDetails()) {
                                var receptionItem = receptionByProductId.get(detailResp.getProductId());
                                if (receptionItem != null && receptionItem.getLots() != null) {
                                        List<LotReceptionResponseDTO> lotResponses = receptionItem.getLots().stream()
                                                        .map(lot -> new LotReceptionResponseDTO(lot.getQuantity(), lot.getExpirationDate(), lot.getBatchCode()))
                                                        .toList();
                                        detailResp.setLots(lotResponses);
                                }
                        }
                }
                return responseDTO;
        }

        @Cacheable(value = "orders_pending", key = "'pending'")
        @Transactional(readOnly = true)
        public List<OrderResponseDTO> findPendingReception() {
                return repository.findProjectedByStatus(OrderStatus.PENDING, Pageable.unpaged()).getContent().stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();
        }

        @OrderAuditable(action = "CAMBIO_ESTADO_ORDEN")
        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#orderId"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true)
        })
        @Retryable(includes = {
                        ObjectOptimisticLockingFailureException.class }, maxRetries = 3, delay = 100, multiplier = 2)
        @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
        public Optional<OrderResponseDTO> updateStatus(Integer orderId, OrderStatus newStatus) {
                return repository.findByIdWithDetails(orderId)
                                .map(order -> {
                                        order.setStatus(newStatus);
                                        Order updatedOrder = repository.save(order);
                                        return orderMapper.toResponseDTO(updatedOrder);
                                });
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> getMissingItems(
                        Integer orderId) {
                Order order = repository.findByIdWithDetails(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

                if (!OrderStatus.INCOMPLETE.equals(order.getStatus())) {
                        throw new InvalidOperationException("Solo las ordenes incompletas tienen items faltantes.");
                }

                return order.getDetails().stream()
                                .filter(detail -> {
                                        BigDecimal received = detail.getQuantityReceived() != null
                                                        ? detail.getQuantityReceived()
                                                        : BigDecimal.ZERO;
                                        return detail.getQuantity().compareTo(received) > 0;
                                })
                                .map(detail -> {
                                        OrderDetailResponseDTO dto = new OrderDetailResponseDTO();
                                        dto.setOrderId(order.getId());
                                        dto.setProductId(detail.getProduct().getId());
                                        dto.setProductName(detail.getProduct().getName());
                                        dto.setUnit(detail.getProduct().getUnit());

                                        BigDecimal received = detail.getQuantityReceived() != null
                                                        ? detail.getQuantityReceived()
                                                        : BigDecimal.ZERO;
                                        dto.setQuantity(detail.getQuantity().subtract(received)); // Faltante
                                        dto.setQuantityReceived(BigDecimal.ZERO);

                                        return dto;
                                })
                                .toList();
        }
}
