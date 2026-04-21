package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedBatchDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedCookingDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedOrderDTO;
import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.response.QuarantinedProductInfoDTO;
import com.economato.inventory.application.dto.response.ReverseTraceabilityDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.ProductBatchMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.CrisisAffectedProduct;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.NotificationType;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.WeeklyPlan;
import com.economato.inventory.domain.model.WeeklyPlanSlot;
import com.economato.inventory.domain.model.WeeklyPlanSlotStatus;
import com.economato.inventory.domain.model.WeeklyPlanStatus;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.CrisisAffectedProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

/**
 * Servicio de Trazabilidad y Gestión de Crisis Alimentarias.
 * Se encarga de garantizar la seguridad alimentaria mediante el bloqueo de
 * productos (quincena),
 * el seguimiento de la cadena de suministro (Forward Traceability) y la
 * reconstrucción
 * de cocinados (Reverse Traceability) para auditorías.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TraceabilityService {

        private final ProductRepository productRepository;
        private final OrderRepository orderRepository;
        private final StockLedgerRepository ledgerRepository;
        private final RecipeCookingAuditRepository cookingAuditRepository;
        private final SupplierRepository supplierRepository;
        private final StockLedgerService ledgerService;
        private final ProductBatchService productBatchService;
        private final RoleNotificationService notificationService;
        private final I18nService i18nService;
        private final SecurityContextHelper securityContextHelper;
        private final OrderMapper orderMapper;
        private final RecipeCookingAuditMapper cookingAuditMapper;
        private final StockLedgerMapper ledgerMapper;
        private final ProductBatchMapper productBatchMapper;
        private final ProductBatchRepository productBatchRepository;
        private final StockLedgerBatchDetailRepository stockLedgerBatchDetailRepository;
        private final MeterRegistry meterRegistry;
        private final FoodCrisisRepository foodCrisisRepository;
        private final CrisisAffectedProductRepository crisisAffectedProductRepository;
        private final WeeklyPlanRepository weeklyPlanRepository;
        private final UserRepository userRepository;
        private final ObjectMapper objectMapper;
        private final PersistentNotificationService persistentNotificationService;

        // Usamos aislamiento SERIALIZABLE para evitar que un producto sea vendido o
        // usado
        // en una receta mientras se está procesando su entrada en cuarentena.
        @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "stock_alerts", allEntries = true)
        })
        @RealtimeSync(entityType = "crisis", action = "CREATE",
                affectedDomains = {"crisis", "product", "stock_alerts", "weekly_plan"})
        @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
        public CrisisResponseDTO activateCrisis(CrisisActivationRequestDTO request) {
                log.info("Activating food safety crisis for supplier ID: {}", request.getSupplierId());

                Supplier supplier = supplierRepository.findById(request.getSupplierId())
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_SUPPLIER_NOT_FOUND)));

                List<Product> products = productRepository.findByIdsForUpdate(request.getProductIds());
                if (products.size() != request.getProductIds().size()) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND));
                }

                User currentUser = securityContextHelper.getCurrentUser();
                String crisisCode = "CRISIS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

                FoodCrisis crisis = FoodCrisis.builder()
                                .crisisCode(crisisCode)
                                .supplier(supplier)
                                .reason(request.getReason())
                                .dateFrom(request.getDateFrom())
                                .dateTo(request.getDateTo())
                                .status(FoodCrisis.CrisisStatus.ACTIVE)
                                .activatedBy(currentUser)
                                .activatedAt(LocalDateTime.now())
                                .build();

                crisis = foodCrisisRepository.save(crisis);

                List<CrisisAffectedProduct> affectedProducts = new ArrayList<>();
                List<BatchMovementItem> markerMovements = new ArrayList<>();
                List<StockLedger> txs = new ArrayList<>();
                for (Product product : products) {
                        affectedProducts.add(CrisisAffectedProduct.builder()
                                        .foodCrisis(crisis)
                                        .product(product)
                                        .originalAvailabilityPercentage(product.getAvailabilityPercentage() != null
                                                        ? product.getAvailabilityPercentage()
                                                        : BigDecimal.valueOf(100.00))
                                        .build());

                        List<ProductBatch> implicatedBatches = productBatchRepository.findByProductIdInOrderByExpirationDateAsc(List.of(product.getId()))
                                        .stream()
                                        .filter(batch -> batch.getRemainingQuantity() != null
                                                        && batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                                        .filter(batch -> !batch.isDepleted())
                                        .filter(batch -> isWithinDateRange(batch.getReceivedAt(), request.getDateFrom(),
                                                        request.getDateTo()))
                                        .toList();

                        if (implicatedBatches.isEmpty()) {
                                markerMovements.add(new BatchMovementItem(
                                                product.getId(),
                                                BigDecimal.ZERO,
                                                MovementType.CUARENTENA,
                                                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE,
                                                                new Object[] { crisisCode }),
                                                null));
                                continue;
                        }

                        for (ProductBatch batch : implicatedBatches) {
                                txs.add(ledgerService.recordManualAdjustment(
                                                product.getId(),
                                                batch.getRemainingQuantity().negate(),
                                                MovementType.CUARENTENA,
                                                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE,
                                                                new Object[] { crisisCode }) + " [batch #"
                                                                + batch.getId()
                                                                + (batch.getBatchCode() != null ? " / " + batch.getBatchCode() : "")
                                                                + "]",
                                                currentUser,
                                                batch.getId(),
                                                batch.getExpirationDate()));
                        }
                }

                productRepository.updateAvailabilityForProducts(request.getProductIds(), BigDecimal.ZERO);
                crisisAffectedProductRepository.saveAll(affectedProducts);

                cancelAffectedWeeklyPlans(request.getProductIds(), crisisCode);

                if (!markerMovements.isEmpty()) {
                        txs.addAll(ledgerService.recordBatchStockMovements(markerMovements, currentUser, null));
                }

                Map<String, QuarantinedProductInfoDTO> quarantinedProductsInfo = resolveLatestBatchInfoByProduct(
                                affectedProducts);
                Map<String, String> quarantinedProducts = resolveLatestBatchLabelsByProduct(affectedProducts,
                                quarantinedProductsInfo);

                broadcastCrisisNotification(
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_TITLE),
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_MESSAGE,
                                                new Object[] { supplier.getName(), quarantinedProducts.keySet(),
                                                                request.getReason() }),
                                AlertCode.FOOD_CRISIS_ACTIVATED,
                                crisis.getId());

                meterRegistry.counter("food.crisis.active.count").increment();

                return buildCrisisResponse(crisis, quarantinedProducts);
        }

        @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "stock_alerts", allEntries = true)
        })
        @RealtimeSync(entityType = "crisis", action = "UPDATE",
                affectedDomains = {"crisis", "product", "stock_alerts", "weekly_plan"})
        @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
        public void liftCrisis(CrisisLiftRequestDTO request) {
                FoodCrisis crisis = foodCrisisRepository.findById(request.getCrisisId())
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

                if (crisis.getStatus() != FoodCrisis.CrisisStatus.ACTIVE) {
                        throw new InvalidOperationException(
                                        i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
                }

                List<CrisisAffectedProduct> associations = crisisAffectedProductRepository
                                .findByFoodCrisisIdWithProduct(crisis.getId());
                if (associations.isEmpty()) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND));
                }

                List<Integer> productIds = associations.stream()
                                .map(ap -> ap.getProduct().getId())
                                .toList();
                List<Product> lockedProducts = productRepository.findByIdsForUpdate(productIds);
                Map<Integer, Product> productsById = lockedProducts.stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));

                User currentUser = securityContextHelper.getCurrentUser();
                List<BatchMovementItem> batchMovements = new ArrayList<>();

                for (CrisisAffectedProduct association : associations) {
                        Product product = productsById.get(association.getProduct().getId());
                        if (product == null) {
                                continue;
                        }

                        BigDecimal targetAvailability = request.getAvailabilityPercentage() != null
                                        ? request.getAvailabilityPercentage()
                                        : association.getOriginalAvailabilityPercentage();
                        product.setAvailabilityPercentage(targetAvailability);

                        batchMovements.add(new BatchMovementItem(
                                        product.getId(),
                                        BigDecimal.ZERO,
                                        MovementType.MODIFICACION,
                                        i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE_LIFT,
                                                        new Object[] { targetAvailability }) + " ["
                                                        + crisis.getCrisisCode() + "]",
                                        null));
                }

                crisis.setStatus(FoodCrisis.CrisisStatus.LIFTED);
                crisis.setLiftedBy(currentUser);
                crisis.setLiftedAt(LocalDateTime.now());

                productRepository.saveAll(lockedProducts);
                foodCrisisRepository.save(crisis);
                ledgerService.recordBatchStockMovements(batchMovements, currentUser, null);

                broadcastCrisisNotification(
                                i18nService.getMessage(MessageKey.CRISIS_LIFT_TITLE),
                                i18nService.getMessage(MessageKey.CRISIS_LIFT_MESSAGE,
                                                new Object[] { lockedProducts.stream().map(Product::getName)
                                                                .collect(Collectors.toList()), "RESTORED" }),
                                AlertCode.FOOD_CRISIS_LIFTED,
                                crisis.getId());
        }

        @Transactional(readOnly = true)
        public List<CrisisResponseDTO> getAllCrises() {
                List<FoodCrisis> crises = foodCrisisRepository.findAllWithSupplier();
                return buildCrisisResponsesBatch(crises);
        }

        @Transactional(readOnly = true)
        public Page<CrisisResponseDTO> getCrisisHistory(String search, Pageable pageable) {
                Page<FoodCrisis> crisisPage = foodCrisisRepository.findHistoryWithSearch(search, pageable);
                List<CrisisResponseDTO> content = buildCrisisResponsesBatch(crisisPage.getContent());
                return new PageImpl<>(content, pageable, crisisPage.getTotalElements());
        }

        @Transactional(readOnly = true)
        public CrisisResponseDTO getCrisisById(Long crisisId) {
                FoodCrisis crisis = foodCrisisRepository.findByIdWithDetails(crisisId)
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
                return buildCrisisResponse(crisis, null);
        }

        @Transactional(readOnly = true)
        public ForwardTraceabilityDTO getForwardTraceability(Integer supplierId, List<Integer> productIds,
                        LocalDateTime from, LocalDateTime to) {
                Supplier supplier = supplierRepository.findById(supplierId)
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_SUPPLIER_NOT_FOUND)));

                List<Order> orders = orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(supplierId,
                                productIds, from, to);
                List<StockLedger> entries = ledgerRepository.findEntradasWithOrderIdByProductIdsAndDateRange(productIds,
                                from, to);
                entries.addAll(ledgerRepository.findSalidasByProductIdsAndDateRange(productIds, from, to));

                List<RecipeCookingAudit> cookings = cookingAuditRepository
                                .findAffectedCookingsByProductIdsAndDateRange(productIds, from, to);

                List<ProductBatch> affectedBatches = productBatchRepository
                                .findByProductIdInOrderByExpirationDateAsc(productIds)
                                .stream()
                                .filter(batch -> isWithinDateRange(batch.getReceivedAt(), from, to))
                                .toList();

                return ForwardTraceabilityDTO.builder()
                                .supplierName(supplier.getName())
                                .productNames(productRepository.findAllById(productIds).stream().map(Product::getName)
                                                .toList())
                                .fromDate(from)
                                .toDate(to)
                                .affectedOrders(orders.stream().map(orderMapper::toResponseDTO)
                                                .collect(Collectors.toList()))
                                .ledgerEntries(entries.stream().map(ledgerMapper::toDTO).collect(Collectors.toList()))
                                .affectedCookings(cookings.stream().map(cookingAuditMapper::toResponseDTO)
                                                .collect(Collectors.toList()))
                                .affectedBatches(affectedBatches.stream()
                                                .map(productBatchMapper::toResponseDTO)
                                                .collect(Collectors.toList()))
                                .build();
        }

        /**
         * Reconstruye el árbol de ingredientes de un cocinado específico.
         * Utiliza el 'componentsState' (instantánea de la receta al momento de cocinar)
         * para garantizar que la trazabilidad sea real, incluso si la receta original
         * cambió.
         */
        @Transactional(readOnly = true)
        public ReverseTraceabilityDTO getReverseTraceability(Long cookingAuditId) {
                RecipeCookingAudit audit = cookingAuditRepository.findByIdWithDetails(cookingAuditId)
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

                Map<String, Object> state = parseDetails(audit.getComponentsState());
                if (state == null || !state.containsKey("components")) {
                        return ReverseTraceabilityDTO.builder()
                                        .cookingAudit(cookingAuditMapper.toResponseDTO(audit))
                                        .ingredientTrace(Collections.emptyList())
                                        .build();
                }

                List<Map<String, Object>> components = (List<Map<String, Object>>) state.get("components");
                List<Integer> productIds = components.stream()
                                .map(comp -> {
                                        Object rawId = comp.get("productId");
                                        return rawId instanceof Number ? ((Number) rawId).intValue() : null;
                                })
                                .filter(Objects::nonNull)
                                .toList();

                // 1. Pre-cargar Productos con Proveedor (JOIN FETCH)
                Map<Integer, Product> productsById = productRepository.findAllByIdWithSupplier(productIds).stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));

                // 2. Pre-cargar Ledger Entries (Last ENTRADA before cooking)
                Map<Integer, StockLedger> lastEntradas = ledgerRepository
                                .findLastEntradasBeforeDateBatch(productIds, audit.getCookingDate()).stream()
                                .collect(Collectors.toMap(
                                                row -> (Integer) row[0],
                                                row -> (StockLedger) row[1]));

                // 3. Pre-cargar Órdenes
                List<Integer> orderIds = lastEntradas.values().stream()
                                .map(StockLedger::getOrderId)
                                .filter(Objects::nonNull)
                                .toList();

                Map<Integer, Order> ordersById = orderIds.isEmpty() ? Collections.emptyMap()
                                : orderRepository.findAllByIdWithDetails(orderIds).stream()
                                                .collect(Collectors.toMap(Order::getId, o -> o));

                Map<Long, ProductBatch> batchesByLedgerTxId = new HashMap<>();
                List<Long> ledgerTxIds = lastEntradas.values().stream()
                                .map(StockLedger::getId)
                                .toList();
                if (!ledgerTxIds.isEmpty()) {
                        List<StockLedgerBatchDetail> batchDetails = stockLedgerBatchDetailRepository
                                        .findByLedgerTransactionIdIn(ledgerTxIds);
                        for (StockLedgerBatchDetail detail : batchDetails) {
                                if (detail.getLedgerTransaction() != null && detail.getBatch() != null) {
                                        batchesByLedgerTxId.putIfAbsent(detail.getLedgerTransaction().getId(),
                                                        detail.getBatch());
                                }
                        }
                }

                List<ReverseTraceabilityDTO.IngredientTraceDTO> ingredientTrace = new ArrayList<>();

                for (Map<String, Object> comp : components) {
                        Object rawId = comp.get("productId");
                        Integer productId = rawId instanceof Number ? ((Number) rawId).intValue() : null;
                        String productName = (String) comp.get("productName");

                        if (productId == null)
                                continue;

                        StockLedger le = lastEntradas.get(productId);
                        ReverseTraceabilityDTO.IngredientTraceDTO.IngredientTraceDTOBuilder builder = ReverseTraceabilityDTO.IngredientTraceDTO
                                        .builder()
                                        .productName(productName);

                        if (le != null) {
                                builder.ledgerHash(le.getCurrentHash())
                                                .orderId(le.getOrderId())
                                                .movementType(le.getMovementType() != null
                                                                ? le.getMovementType().name()
                                                                : null)
                                                .description(le.getDescription());

                                ProductBatch relatedBatch = batchesByLedgerTxId.get(le.getId());
                                if (relatedBatch != null) {
                                        builder.batchId(relatedBatch.getId())
                                                        .batchCode(relatedBatch.getBatchCode())
                                                        .expirationDate(relatedBatch.getExpirationDate())
                                                        .batchExpirationDate(relatedBatch.getExpirationDate())
                                                        .batchInitialQuantity(relatedBatch.getInitialQuantity())
                                                        .batchRemainingQuantity(relatedBatch.getRemainingQuantity())
                                                        .batchReceivedAt(relatedBatch.getReceivedAt())
                                                        .batchDepleted(relatedBatch.isDepleted());
                                }

                                if (le.getOrderId() != null && ordersById.containsKey(le.getOrderId())) {
                                        Order o = ordersById.get(le.getOrderId());
                                        builder.supplierName(o.getSupplier().getName());
                                        builder.orderDate(o.getOrderDate());
                                        if (o.getUser() != null) {
                                                builder.orderUserName(o.getUser().getName());
                                        }
                                } else {
                                        Product p = productsById.get(productId);
                                        if (p != null) {
                                                if (p.getSupplier() != null) {
                                                        builder.supplierName(p.getSupplier().getName()
                                                                        + " (Por defecto)");
                                                } else {
                                                        builder.supplierName("Sin proveedor");
                                                }
                                        }
                                }
                        }

                        ingredientTrace.add(builder.build());
                }

                return ReverseTraceabilityDTO.builder()
                                .cookingAudit(cookingAuditMapper.toResponseDTO(audit))
                                .ingredientTrace(ingredientTrace)
                                .build();
        }

        @Transactional(readOnly = true)
        public List<com.economato.inventory.application.dto.response.RecipeCookingAuditResponseDTO> getCookingAuditsByBatchId(Long batchId) {
                return cookingAuditRepository.findByBatchId(batchId).stream()
                                .map(cookingAuditMapper::toResponseDTO)
                                .collect(Collectors.toList());
        }

        private List<CrisisResponseDTO> buildCrisisResponsesBatch(List<FoodCrisis> crises) {
                if (crises.isEmpty())
                        return Collections.emptyList();

                List<Long> crisisIds = crises.stream().map(FoodCrisis::getId).toList();

                Map<Long, List<CrisisAffectedProduct>> associationsByCrisis = crisisAffectedProductRepository
                                .findByFoodCrisisIdIn(crisisIds).stream()
                                .collect(Collectors.groupingBy(ap -> ap.getFoodCrisis().getId()));

                Set<Integer> allProductIdsSet = associationsByCrisis.values().stream()
                                .flatMap(List::stream)
                                .map(ap -> ap.getProduct().getId())
                                .collect(Collectors.toSet());
                List<Integer> allProductIds = new ArrayList<>(allProductIdsSet);

                LocalDateTime minDate = crises.stream().map(FoodCrisis::getDateFrom).filter(Objects::nonNull)
                                .min(LocalDateTime::compareTo).orElse(null);
                LocalDateTime maxDate = crises.stream().map(FoodCrisis::getDateTo).filter(Objects::nonNull)
                                .max(LocalDateTime::compareTo).orElse(null);

                List<Order> allAffectedOrders = orderRepository.findConfirmedOrdersByProductIdsAndDateRange(allProductIds,
                                minDate, maxDate);

                List<RecipeCookingAudit> allAffectedCookings = cookingAuditRepository
                                .findAffectedCookingsByProductIdsAndDateRange(allProductIds, minDate, maxDate);

                List<IntegrityCheckResult> integrityResultsBatch = ledgerService
                                .verifyChainIntegrityBatch(allProductIds);
                Map<Integer, Boolean> integrityByProduct = integrityResultsBatch.stream()
                                .collect(Collectors.toMap(
                                                IntegrityCheckResult::getProductId,
                                                IntegrityCheckResult::isValid));

                return crises.stream().map(crisis -> {
                        List<CrisisAffectedProduct> associations = associationsByCrisis.getOrDefault(crisis.getId(),
                                        Collections.emptyList());
                        List<Integer> crisisProductIds = associations.stream().map(ap -> ap.getProduct().getId()).toList();

                        List<Order> crisisOrders = allAffectedOrders.stream()
                                        .filter(o -> crisisProductIds.contains(o.getDetails().stream()
                                                        .map(d -> d.getProduct().getId()).findFirst().orElse(null))
                                                        && isWithinDateRange(o.getOrderDate(), crisis.getDateFrom(),
                                                                        crisis.getDateTo()))
                                        .toList();

                        List<RecipeCookingAudit> crisisCookings = allAffectedCookings.stream()
                                        .filter(c -> isWithinDateRange(c.getCookingDate(), crisis.getDateFrom(),
                                                        crisis.getDateTo()))
                                        .toList();

                        return buildCrisisResponse(crisis, null, associations, crisisOrders, crisisCookings,
                                        integrityByProduct);
                }).collect(Collectors.toList());
        }

        private CrisisResponseDTO buildCrisisResponse(FoodCrisis crisis,
                        Map<String, String> quarantinedProductsOverride) {
                List<CrisisAffectedProduct> associations = crisisAffectedProductRepository
                                .findByFoodCrisisIdWithProduct(crisis.getId());
                List<Integer> productIds = associations.stream().map(ap -> ap.getProduct().getId()).toList();

                List<Order> affectedOrders = orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(
                                crisis.getSupplier().getId(),
                                productIds,
                                crisis.getDateFrom(),
                                crisis.getDateTo());

                List<RecipeCookingAudit> affectedCookings = cookingAuditRepository
                                .findAffectedCookingsByProductIdsAndDateRange(productIds, crisis.getDateFrom(),
                                                crisis.getDateTo());

                List<IntegrityCheckResult> batchResults = ledgerService.verifyChainIntegrityBatch(productIds);
                Map<Integer, Boolean> integrityResults = batchResults.stream()
                                .collect(Collectors.toMap(IntegrityCheckResult::getProductId, IntegrityCheckResult::isValid));

                return buildCrisisResponse(crisis, quarantinedProductsOverride, associations, affectedOrders,
                                affectedCookings, integrityResults);
        }

        private CrisisResponseDTO buildCrisisResponse(
                        FoodCrisis crisis,
                        Map<String, String> quarantinedProductsOverride,
                        List<CrisisAffectedProduct> associations,
                        List<Order> affectedOrders,
                        List<RecipeCookingAudit> affectedCookings,
                        Map<Integer, Boolean> preloadedIntegrityResults) {

                Map<String, QuarantinedProductInfoDTO> quarantinedProductsInfo = resolveLatestBatchInfoByProduct(
                                associations);

                Map<String, String> quarantinedProducts = quarantinedProductsOverride != null
                                ? quarantinedProductsOverride
                                : resolveLatestBatchLabelsByProduct(associations, quarantinedProductsInfo);

                List<Integer> productIds = associations.stream().map(ap -> ap.getProduct().getId()).toList();
                boolean integrityVerified = productIds.stream()
                                .allMatch(p -> preloadedIntegrityResults.getOrDefault(p, false));

                return CrisisResponseDTO.builder()
                                .crisisId(crisis.getId())
                                .crisisCode(crisis.getCrisisCode())
                                .status(crisis.getStatus().name())
                                .reason(crisis.getReason())
                                .supplierName(crisis.getSupplier().getName())
                                .quarantinedProducts(quarantinedProducts)
                                .quarantinedProductsInfo(quarantinedProductsInfo)
                                .affectedBatches(buildAffectedBatchDetails(crisis, associations))
                                .affectedOrderIds(affectedOrders.stream().map(Order::getId).toList())
                                .affectedOrders(affectedOrders.stream().map(o -> CrisisAffectedOrderDTO.builder()
                                                .orderId(o.getId())
                                                .supplierName(o.getSupplier() != null ? o.getSupplier().getName()
                                                                : crisis.getSupplier().getName())
                                                .status(o.getStatus().name())
                                                .createdAt(o.getOrderDate())
                                                .totalItems(o.getDetails() != null ? o.getDetails().size() : 0)
                                                .build()).toList())
                                .affectedCookingAuditIds(
                                                affectedCookings.stream().map(RecipeCookingAudit::getId).toList())
                                .affectedCookings(affectedCookings.stream().map(c -> CrisisAffectedCookingDTO.builder()
                                                .cookingAuditId(c.getId())
                                                .recipeName(c.getRecipe() != null ? c.getRecipe().getName() : "–")
                                                .userName(c.getUser() != null ? c.getUser().getName() : "–")
                                                .cookingDate(c.getCookingDate())
                                                .quantityCooked(c.getQuantityCooked() != null
                                                                ? c.getQuantityCooked().doubleValue()
                                                                : 0.0)
                                                .build()).toList())
                                .integrityVerified(integrityVerified)
                                .summary(i18nService.getMessage(MessageKey.TRACEABILITY_SUMMARY_FORWARD,
                                                new Object[] { crisis.getSupplier().getName(), crisis.getDateFrom(),
                                                                crisis.getDateTo() }))
                                .timestamp(crisis.getActivatedAt())
                                .build();
        }

        private Map<String, QuarantinedProductInfoDTO> resolveLatestBatchInfoByProduct(
                        List<CrisisAffectedProduct> associations) {
                if (associations.isEmpty()) {
                        return Collections.emptyMap();
                }

                List<Integer> productIds = associations.stream().map(ap -> ap.getProduct().getId()).toList();
                List<ProductBatch> batches = productBatchRepository.findByProductIdInOrderByExpirationDateAsc(productIds);

                Map<Integer, ProductBatch> selectedByProduct = new LinkedHashMap<>();
                for (ProductBatch batch : batches) {
                        selectedByProduct.computeIfAbsent(batch.getProduct().getId(), ignored -> batch);
                }

                Map<Integer, String> latestHashes = ledgerRepository.findLatestHashesByProductIds(productIds).stream()
                                .collect(Collectors.toMap(
                                                row -> (Integer) row[0],
                                                row -> (String) row[1]));

                Map<String, QuarantinedProductInfoDTO> infoMap = new LinkedHashMap<>();
                for (CrisisAffectedProduct association : associations) {
                        Product product = association.getProduct();
                        ProductBatch batch = selectedByProduct.get(product.getId());

                        QuarantinedProductInfoDTO info = QuarantinedProductInfoDTO.builder()
                                        .batchId(batch != null ? batch.getId() : null)
                                        .batchCode(batch != null ? batch.getBatchCode() : null)
                                        .expirationDate(batch != null ? batch.getExpirationDate() : null)
                                        .initialQuantity(batch != null ? batch.getInitialQuantity() : null)
                                        .remainingQuantity(batch != null ? batch.getRemainingQuantity() : null)
                                        .receivedAt(batch != null ? batch.getReceivedAt() : null)
                                        .depleted(batch != null ? batch.isDepleted() : null)
                                        .ledgerHash(latestHashes.get(product.getId()))
                                        .build();

                        infoMap.put(product.getName(), info);
                }
                return infoMap;
        }

        private Map<String, String> resolveLatestBatchLabelsByProduct(
                        List<CrisisAffectedProduct> associations,
                        Map<String, QuarantinedProductInfoDTO> infoByProduct) {
                Map<String, String> labels = new LinkedHashMap<>();
                for (CrisisAffectedProduct association : associations) {
                        Product product = association.getProduct();
                        QuarantinedProductInfoDTO info = infoByProduct.get(product.getName());

                        if (info != null && info.getBatchId() != null) {
                                String code = info.getBatchCode() != null && !info.getBatchCode().isBlank()
                                                ? " / " + info.getBatchCode()
                                                : "";
                                labels.put(product.getName(), "Lote #" + info.getBatchId() + code);
                        } else {
                                labels.put(product.getName(), "Sin lote asociado");
                        }
                }
                return labels;
        }

        private List<CrisisAffectedBatchDTO> buildAffectedBatchDetails(FoodCrisis crisis,
                        List<CrisisAffectedProduct> associations) {
                if (associations.isEmpty()) {
                        return List.of();
                }

                List<Integer> productIds = associations.stream()
                                .map(ap -> ap.getProduct().getId())
                                .toList();

                List<ProductBatch> allRelevantBatches = productBatchRepository.findByProductIdInOrderByExpirationDateAsc(productIds);
                Map<Long, ProductBatch> batchesById = new LinkedHashMap<>();
                
                for (ProductBatch batch : allRelevantBatches) {
                        if (isWithinDateRange(batch.getReceivedAt(), crisis.getDateFrom(), crisis.getDateTo())) {
                                batchesById.put(batch.getId(), batch);
                        }
                }

                return batchesById.values().stream()
                                .sorted(Comparator
                                                .comparing(ProductBatch::getExpirationDate,
                                                                Comparator.nullsLast(LocalDate::compareTo))
                                                .thenComparing(ProductBatch::getId))
                                .map(batch -> CrisisAffectedBatchDTO.builder()
                                                .batchId(batch.getId())
                                                .productId(batch.getProduct().getId())
                                                .productName(batch.getProduct().getName())
                                                .expirationDate(batch.getExpirationDate())
                                                .batchCode(batch.getBatchCode())
                                                .remainingQuantity(batch.getRemainingQuantity())
                                                .expired(batch.getExpirationDate() != null
                                                                && batch.getExpirationDate().isBefore(LocalDate.now()))
                                                .depleted(batch.isDepleted())
                                                .build())
                                .toList();
        }

        private boolean isWithinDateRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
                if (value == null) {
                        return false;
                }
                boolean afterFrom = from == null || !value.isBefore(from);
                boolean beforeTo = to == null || !value.isAfter(to);
                return afterFrom && beforeTo;
        }

        private void broadcastCrisisNotification(String title, String body, AlertCode code, Long crisisId) {
                RoleNotificationMessage message = RoleNotificationMessage.builder()
                                .title(title)
                                .message(body)
                                .code(code)
                                .timestamp(LocalDateTime.now())
                                .build();

                for (Role role : Role.values()) {
                        notificationService.sendNotificationToRole(role, message);
                }

                persistentNotificationService.notifyCrisis(title, body, code, crisisId);
        }

        private void cancelAffectedWeeklyPlans(List<Integer> affectedProductIds, String crisisCode) {
                if (weeklyPlanRepository == null || userRepository == null) {
                        return;
                }

                List<WeeklyPlan> activePlans = weeklyPlanRepository.findActivePlansWithPendingSlots();
                for (WeeklyPlan plan : activePlans) {
                        boolean affected = false;

                        for (WeeklyPlanSlot slot : plan.getSlots()) {
                                if (slot.getStatus() != WeeklyPlanSlotStatus.PENDING
                                                && slot.getStatus() != WeeklyPlanSlotStatus.IN_PROGRESS) {
                                        continue;
                                }

                                boolean slotAffected = slot.getRecipe().getComponents().stream()
                                                .anyMatch(rc -> affectedProductIds.contains(rc.getProduct().getId()));
                                if (slotAffected) {
                                        slot.setStatus(WeeklyPlanSlotStatus.CANCELLED);
                                        affected = true;
                                }
                        }

                        if (!affected) {
                                continue;
                        }

                        boolean hasConfirmedSlots = plan.getSlots().stream()
                                        .anyMatch(slot -> slot.getStatus() == WeeklyPlanSlotStatus.CONFIRMED);
                        boolean hasOpenSlots = plan.getSlots().stream()
                                        .anyMatch(slot -> slot.getStatus() == WeeklyPlanSlotStatus.PENDING
                                                        || slot.getStatus() == WeeklyPlanSlotStatus.IN_PROGRESS);

                        if (hasConfirmedSlots && hasOpenSlots) {
                                plan.setStatus(WeeklyPlanStatus.IN_PROGRESS);
                        } else if (hasConfirmedSlots) {
                                plan.setStatus(WeeklyPlanStatus.COMPLETED);
                        } else if (hasOpenSlots) {
                                plan.setStatus(WeeklyPlanStatus.ACTIVE);
                        } else {
                                plan.setStatus(WeeklyPlanStatus.CANCELLED);
                        }

                        weeklyPlanRepository.save(plan);

                        List<User> recipients = new ArrayList<>();
                        recipients.addAll(userRepository.findByRoleAndIsHiddenFalse(Role.ADMIN));
                        if (plan.getChef() != null) {
                                recipients.add(plan.getChef());
                        }

                        List<User> uniqueRecipients = recipients.stream()
                                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left,
                                                        java.util.LinkedHashMap::new))
                                        .values().stream().toList();

                        String chefName = plan.getChef() != null ? plan.getChef().getName() : "N/A";
                        String title = i18nService.getMessage(MessageKey.NOTIFICATION_PLAN_CANCELLED,
                                        new Object[] { chefName, plan.getId(), crisisCode });
                        persistentNotificationService.notifyUsersOfType(NotificationType.WEEKLY_PLAN_CANCELLED,
                                        title, title, plan.getId(), uniqueRecipients);
                }
        }

        private Map<String, Object> parseDetails(String detailsJson) {
                if (detailsJson == null || detailsJson.isEmpty()) {
                        return Collections.emptyMap();
                }
                try {
                        return objectMapper.readValue(detailsJson, new TypeReference<Map<String, Object>>() {
                        });
                } catch (Exception e) {
                        log.warn("Failed to parse audit details JSON: {}", detailsJson);
                        return Collections.emptyMap();
                }
        }
}
