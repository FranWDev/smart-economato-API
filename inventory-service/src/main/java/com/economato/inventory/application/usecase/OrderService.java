package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.economato.inventory.application.dto.request.OrdersByProductsRequestDTO;
import com.economato.inventory.application.dto.response.LotReceptionResponseDTO;
import com.economato.inventory.application.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.application.dto.response.OrderFilterResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.application.dto.response.OrderTotalCostResponseDTO;
import com.economato.inventory.application.dto.response.OrdersByProductsResponseDTO;
import com.economato.inventory.application.dto.response.ProductOrderQuantityResponseDTO;
import com.economato.inventory.application.dto.response.UserResponseDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.domain.OrderAuditable;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.OrderDetail;
import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.OrderReceptionAlreadyProcessedException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.specification.OrderSpecifications;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;
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
        private final FoodCrisisRepository foodCrisisRepository;
        private final OrderMapper orderMapper;
        private final StockLedgerService stockLedgerService;
        private final ProductBatchService productBatchService;
        private final OrderReviewLockService orderReviewLockService;
        private final Environment environment;

        public OrderService(I18nService i18nService, OrderRepository repository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        SupplierRepository supplierRepository,
                        FoodCrisisRepository foodCrisisRepository,
                        OrderMapper orderMapper,
                        StockLedgerService stockLedgerService,
                        ProductBatchService productBatchService,
                        OrderReviewLockService orderReviewLockService,
                        Environment environment) {
                this.i18nService = i18nService;
                this.repository = repository;
                this.userRepository = userRepository;
                this.productRepository = productRepository;
                this.supplierRepository = supplierRepository;
                this.foodCrisisRepository = foodCrisisRepository;
                this.orderMapper = orderMapper;
                this.stockLedgerService = stockLedgerService;
                this.productBatchService = productBatchService;
                this.orderReviewLockService = orderReviewLockService;
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
                        @CacheEvict(value = "orders_pending", allEntries = true),
                        @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
                        @CacheEvict(value = "weekly_plan", allEntries = true)
        })
        @RealtimeSync(entityType = "order", action = "CREATE",
                affectedDomains = {"order", "product", "weekly_plan", "stock_alerts"},
                idsFromResult = "orderProductIds")
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
                                                        i18nService.getMessage(MessageKey.ERROR_SUPPLIER_NOT_FOUND)));
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
                return repository.findProjectedById(savedOrder.getId())
                                .map(projection -> orderMapper.toResponseDTO(projection))
                                .orElseGet(() -> orderMapper.toResponseDTO(savedOrder));
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
        @RealtimeSync(entityType = "order", action = "UPDATE", idFromArg = 0,
                affectedDomains = {"order", "product", "weekly_plan", "stock_alerts"},
                idsFromResult = "orderProductIds")
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
                                                                                i18nService.getMessage(MessageKey.ERROR_SUPPLIER_NOT_FOUND)));
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
                                        return repository.findProjectedById(saved.getId())
                                                        .map(projection -> orderMapper.toResponseDTO(projection))
                                                        .orElseGet(() -> orderMapper.toResponseDTO(saved));
                                });
        }

        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#id"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true)
        })
        @RealtimeSync(entityType = "order", action = "DELETE", idFromArg = 0,
                affectedDomains = {"order", "product", "weekly_plan", "stock_alerts"})
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
                                        i18nService.getMessage(MessageKey.ERROR_CONSUMPTION_INVALID_DATE_RANGE));
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

        @Transactional(readOnly = true)
        public OrdersByProductsResponseDTO findByProducts(OrdersByProductsRequestDTO requestDTO) {
                if (requestDTO == null || requestDTO.getProductIds() == null || requestDTO.getProductIds().isEmpty()) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_SEARCH_MISSING_PRODUCTS));
                }

                final List<Integer> productIds = requestDTO.getProductIds().stream()
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList();
                if (productIds.isEmpty()) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_SEARCH_INVALID_IDS));
                }

                final List<OrderStatus> statuses = (requestDTO.getStatuses() == null || requestDTO.getStatuses().isEmpty())
                                ? List.of(OrderStatus.CREATED, OrderStatus.PENDING, OrderStatus.REVIEW)
                                : requestDTO.getStatuses();

                // Anti N+1 by design: fixed amount of queries independent of number of products.
                final List<Integer> orderIds = repository.findIdsByStatusInAndDetailProductIdIn(statuses, productIds);
                if (orderIds.isEmpty()) {
                        return OrdersByProductsResponseDTO.builder()
                                        .orders(Collections.emptyList())
                                        .totalQuantityPerProduct(Collections.emptyMap())
                                        .orderCountPerProduct(Collections.emptyMap())
                                        .ordersByProduct(Collections.emptyMap())
                                        .build();
                }

                final List<Order> orders = repository.findAllByIdWithDetails(orderIds);
                final HashSet<Integer> productSet = new HashSet<>(productIds);

                final Map<Integer, BigDecimal> totalsByProduct = new HashMap<>();
                final Map<Integer, HashSet<Integer>> orderIdSetByProduct = new HashMap<>();
                final Map<Integer, List<ProductOrderQuantityResponseDTO>> breakdownByProduct = new HashMap<>();

                for (Order order : orders) {
                        for (OrderDetail detail : order.getDetails()) {
                                final Integer productId = detail.getProduct().getId();
                                if (!productSet.contains(productId)) {
                                        continue;
                                }

                                totalsByProduct.merge(productId, detail.getQuantity(), BigDecimal::add);
                                orderIdSetByProduct.computeIfAbsent(productId, k -> new HashSet<>()).add(order.getId());

                                final ProductOrderQuantityResponseDTO perOrder = new ProductOrderQuantityResponseDTO(
                                                order.getId(),
                                                order.getStatus(),
                                                detail.getQuantity(),
                                                order.getSupplier() != null ? order.getSupplier().getName() : null,
                                                order.getOrderDate());
                                breakdownByProduct.computeIfAbsent(productId, k -> new ArrayList<>())
                                                .add(perOrder);
                        }
                }

                final Map<Integer, Integer> orderCountByProduct = orderIdSetByProduct.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

                final List<OrderResponseDTO> orderDTOs = orders.stream()
                                .map(orderMapper::toResponseDTO)
                                .toList();

                return OrdersByProductsResponseDTO.builder()
                                .orders(orderDTOs)
                                .totalQuantityPerProduct(totalsByProduct)
                                .orderCountPerProduct(orderCountByProduct)
                                .ordersByProduct(breakdownByProduct)
                                .build();
        }

        /**
         * Procesa la recepción de una orden, validando que no haya menores cantidades
         * y actualizando el inventario con las cantidades recibidas.
         * 
         * Utiliza Pessimistic Locking para garantizar consistencia en el stock.
         */
        @RealtimeSync(entityType = "order", action = "RECEIVE", idFromArg = -2,
                affectedDomains = {"order", "ledger", "product", "weekly_plan", "stock_alerts"},
                idsFromResult = "orderProductIds")
        @PredictorTrigger(action = "ORDER_RECEPTION")
        @OrderAuditable(action = "RECEPCION_ORDEN")
        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#receptionData.orderId"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "products_page", allEntries = true),
                        @CacheEvict(value = "product", allEntries = true),
                        @CacheEvict(value = "products_search", allEntries = true),
                        @CacheEvict(value = "product_stats", allEntries = true),
                        @CacheEvict(value = "cookable_recipes", allEntries = true),
                        @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
                        @CacheEvict(value = "stock_alerts", allEntries = true),
                        @CacheEvict(value = "stock_predictions", allEntries = true),
                        @CacheEvict(value = "weekly_consumption", allEntries = true),
                        @CacheEvict(value = "daily_forecast", allEntries = true)
        })
        @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
                        RuntimeException.class,
                        Exception.class }, isolation = Isolation.REPEATABLE_READ)
        public OrderResponseDTO receiveOrder(OrderReceptionRequestDTO receptionData) {
                orderReviewLockService.assertCanProcessReception(receptionData.getOrderId());

                Order order = repository.findByIdWithDetails(receptionData.getOrderId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

                if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.INCOMPLETE) {
                        throw new OrderReceptionAlreadyProcessedException(order.getId(), order.getStatus());
                }

                if (order.getStatus() != OrderStatus.REVIEW) {
                        throw new InvalidOperationException(
                                        i18nService.getMessage(MessageKey.ERROR_ORDER_INVALID_STATE, order.getStatus()));
                }

                if (foodCrisisRepository != null
                                && order.getSupplier() != null
                                && foodCrisisRepository.existsByStatusAndSupplierId(FoodCrisis.CrisisStatus.ACTIVE,
                                                order.getSupplier().getId())) {
                        throw new InvalidOperationException(
                                        i18nService.getMessage(MessageKey.ERROR_ORDER_SUPPLIER_IN_CRISIS));
                }

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
                orderReviewLockService.releaseLockAfterReviewCompletion(order.getId());
                OrderResponseDTO responseDTO = repository.findProjectedById(savedOrder.getId())
                                .map(projection -> orderMapper.toResponseDTO(projection))
                                .orElseGet(() -> orderMapper.toResponseDTO(savedOrder));

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

        @RealtimeSync(entityType = "order", action = "STATUS_CHANGE", idFromArg = 0,
                affectedDomains = {"order", "product", "weekly_plan", "stock_alerts"},
                idsFromResult = "orderProductIds")
        @OrderAuditable(action = "CAMBIO_ESTADO_ORDEN")
        @Caching(evict = {
                        @CacheEvict(value = "order", key = "#orderId"),
                        @CacheEvict(value = "orders_page", allEntries = true),
                        @CacheEvict(value = "orders_pending", allEntries = true),
                        @CacheEvict(value = "order_stats", allEntries = true),
                        @CacheEvict(value = "products_page", allEntries = true),
                        @CacheEvict(value = "product", allEntries = true),
                        @CacheEvict(value = "products_search", allEntries = true),
                        @CacheEvict(value = "product_stats", allEntries = true),
                        @CacheEvict(value = "cookable_recipes", allEntries = true),
                        @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
                        @CacheEvict(value = "stock_alerts", allEntries = true),
                        @CacheEvict(value = "stock_predictions", allEntries = true),
                        @CacheEvict(value = "weekly_consumption", allEntries = true),
                        @CacheEvict(value = "daily_forecast", allEntries = true)
        })
        @Retryable(includes = {
                        ObjectOptimisticLockingFailureException.class }, maxRetries = 3, delay = 100, multiplier = 2)
        @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
        public Optional<OrderResponseDTO> updateStatus(Integer orderId, OrderStatus newStatus) {
                orderReviewLockService.assertCanTransitionOrder(orderId, newStatus);

                return repository.findByIdWithDetails(orderId)
                                .map(order -> {
                                        if ((order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.INCOMPLETE)
                                                        && (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.INCOMPLETE)) {
                                                throw new OrderReceptionAlreadyProcessedException(order.getId(), order.getStatus());
                                        }

                                        order.setStatus(newStatus);
                                        Order updatedOrder = repository.save(order);
                                        if (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED
                                                        || newStatus == OrderStatus.INCOMPLETE) {
                                                orderReviewLockService.releaseLockAfterReviewCompletion(orderId);
                                        }
                                        return repository.findProjectedById(updatedOrder.getId())
                                                        .map(projection -> orderMapper.toResponseDTO(projection))
                                                        .orElseGet(() -> orderMapper.toResponseDTO(updatedOrder));
                                });
        }

        @Transactional(readOnly = true)
        public List<OrderDetailResponseDTO> getMissingItems(
                        Integer orderId) {
                Order order = repository.findByIdWithDetails(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

                if (!OrderStatus.INCOMPLETE.equals(order.getStatus())) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_INCOMPLETE_ONLY_MISSING_ITEMS));
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
