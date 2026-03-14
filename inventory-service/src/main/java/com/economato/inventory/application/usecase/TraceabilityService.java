package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedBatchDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedCookingDTO;
import com.economato.inventory.application.dto.response.CrisisAffectedOrderDTO;
import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.response.ReverseTraceabilityDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.CrisisAffectedProduct;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.CrisisAffectedProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
        private final MeterRegistry meterRegistry;
        private final FoodCrisisRepository foodCrisisRepository;
        private final CrisisAffectedProductRepository crisisAffectedProductRepository;
        private final ObjectMapper objectMapper;

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
                List<BatchMovementItem> batchMovements = new ArrayList<>();
                for (Product product : products) {
                        affectedProducts.add(CrisisAffectedProduct.builder()
                                        .foodCrisis(crisis)
                                        .product(product)
                                        .originalAvailabilityPercentage(product.getAvailabilityPercentage() != null 
                                                        ? product.getAvailabilityPercentage() 
                                                        : BigDecimal.valueOf(100.00))
                                        .build());

                        batchMovements.add(new BatchMovementItem(
                                        product.getId(),
                                        BigDecimal.ZERO,
                                        MovementType.CUARENTENA,
                                        i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE,
                                                        new Object[] { crisisCode }),
                                        null));
                }

                productRepository.updateAvailabilityForProducts(request.getProductIds(), BigDecimal.ZERO);
                crisisAffectedProductRepository.saveAll(affectedProducts);

                List<StockLedger> txs = ledgerService.recordBatchStockMovements(batchMovements, currentUser, null);
                Map<String, String> quarantinedProducts = txs.stream()
                                .collect(Collectors.toMap(
                                                tx -> tx.getProduct().getName(),
                                                StockLedger::getCurrentHash,
                                                (left, right) -> left,
                                                LinkedHashMap::new));

                broadcastCrisisNotification(
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_TITLE),
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_MESSAGE,
                                                new Object[] { supplier.getName(), quarantinedProducts.keySet(),
                                                                request.getReason() }),
                                AlertCode.FOOD_CRISIS_ACTIVATED);

                meterRegistry.counter("food.crisis.active.count").increment();

                return buildCrisisResponse(crisis, quarantinedProducts);
        }

        @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
        public void liftCrisis(CrisisLiftRequestDTO request) {
                FoodCrisis crisis = foodCrisisRepository.findById(request.getCrisisId())
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

                if (crisis.getStatus() != FoodCrisis.CrisisStatus.ACTIVE) {
                        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
                }

                List<CrisisAffectedProduct> associations = crisisAffectedProductRepository.findByFoodCrisisId(crisis.getId());
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
                                                        new Object[] { targetAvailability }) + " [" + crisis.getCrisisCode() + "]",
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
                                AlertCode.FOOD_CRISIS_LIFTED);
        }

        @Transactional(readOnly = true)
        public List<CrisisResponseDTO> getAllCrises() {
                return foodCrisisRepository.findAll().stream()
                                .map(crisis -> buildCrisisResponse(crisis, null))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public Page<CrisisResponseDTO> getCrisisHistory(String search, Pageable pageable) {
                return foodCrisisRepository.findHistoryWithSearch(search, pageable)
                                .map(crisis -> buildCrisisResponse(crisis, null));
        }

        @Transactional(readOnly = true)
        public CrisisResponseDTO getCrisisById(Long crisisId) {
                FoodCrisis crisis = foodCrisisRepository.findById(crisisId)
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
                                .build();
        }

        @Transactional(readOnly = true)
        public ReverseTraceabilityDTO getReverseTraceability(Long cookingAuditId) {
                RecipeCookingAudit audit = cookingAuditRepository.findById(cookingAuditId)
                                .orElseThrow(() -> new InvalidOperationException(
                                                i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        List<ReverseTraceabilityDTO.IngredientTraceDTO> ingredientTrace = new ArrayList<>();
        
        // El estado de los componentes se guarda como JSON en componentsState
        Map<String, Object> state = parseDetails(audit.getComponentsState());
        if (state != null && state.containsKey("components")) {
            List<Map<String, Object>> components = (List<Map<String, Object>>) state.get("components");

            for (Map<String, Object> comp : components) {
                // Jackson puede deserializar números como Integer o Long
                Object rawId = comp.get("productId");
                Integer productId = rawId instanceof Number ? ((Number) rawId).intValue() : null;
                String productName = (String) comp.get("productName");

                if (productId == null) continue;

                Optional<StockLedger> lastEntrada = ledgerRepository
                        .findLastEntradaBeforeDate(productId, audit.getCookingDate());

                ReverseTraceabilityDTO.IngredientTraceDTO.IngredientTraceDTOBuilder builder = ReverseTraceabilityDTO.IngredientTraceDTO
                        .builder()
                        .productName(productName);

                lastEntrada.ifPresent(le -> {
                    builder.ledgerHash(le.getCurrentHash())
                           .orderId(le.getOrderId());

                    if (le.getOrderId() != null) {
                        orderRepository.findById(le.getOrderId()).ifPresent(
                                o -> builder.supplierName(o.getSupplier().getName()));
                    }
                });

                ingredientTrace.add(builder.build());
            }
        }

        return ReverseTraceabilityDTO.builder()
                .cookingAudit(cookingAuditMapper.toResponseDTO(audit))
                .ingredientTrace(ingredientTrace)
                .build();
    }

        private CrisisResponseDTO buildCrisisResponse(FoodCrisis crisis, Map<String, String> quarantinedProductsOverride) {
                List<CrisisAffectedProduct> associations = crisisAffectedProductRepository.findByFoodCrisisId(crisis.getId());
                List<Integer> productIds = associations.stream().map(ap -> ap.getProduct().getId()).toList();

                Map<String, String> quarantinedProducts = quarantinedProductsOverride != null
                                ? quarantinedProductsOverride
                                : resolveLatestHashesByProduct(associations);

                List<Order> affectedOrders = orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(
                                crisis.getSupplier().getId(),
                                productIds,
                                crisis.getDateFrom(),
                                crisis.getDateTo());

                List<RecipeCookingAudit> affectedCookings = cookingAuditRepository
                                .findAffectedCookingsByProductIdsAndDateRange(productIds, crisis.getDateFrom(),
                                                crisis.getDateTo());

                boolean integrityVerified = productIds.stream()
                                .allMatch(p -> ledgerService.verifyChainIntegrity(p).isValid());

                return CrisisResponseDTO.builder()
                                .crisisId(crisis.getId())
                                .crisisCode(crisis.getCrisisCode())
                                .status(crisis.getStatus().name())
                                .reason(crisis.getReason())
                                .supplierName(crisis.getSupplier().getName())
                                .quarantinedProducts(quarantinedProducts)
                                .affectedBatches(buildAffectedBatchDetails(associations))
                                .affectedOrderIds(affectedOrders.stream().map(Order::getId).toList())
                                .affectedOrders(affectedOrders.stream().map(o -> CrisisAffectedOrderDTO.builder()
                                                .orderId(o.getId())
                                                .supplierName(o.getSupplier() != null ? o.getSupplier().getName() : crisis.getSupplier().getName())
                                                .status(o.getStatus().name())
                                                .createdAt(o.getOrderDate())
                                                .totalItems(o.getDetails() != null ? o.getDetails().size() : 0)
                                                .build()).toList())
                                .affectedCookingAuditIds(affectedCookings.stream().map(RecipeCookingAudit::getId).toList())
                                .affectedCookings(affectedCookings.stream().map(c -> CrisisAffectedCookingDTO.builder()
                                                .cookingAuditId(c.getId())
                                                .recipeName(c.getRecipe() != null ? c.getRecipe().getName() : "–")
                                                .userName(c.getUser() != null ? c.getUser().getName() : "–")
                                                .cookingDate(c.getCookingDate())
                                                .quantityCooked(c.getQuantityCooked() != null ? c.getQuantityCooked().doubleValue() : 0.0)
                                                .build()).toList())
                                .integrityVerified(integrityVerified)
                                .summary(i18nService.getMessage(MessageKey.TRACEABILITY_SUMMARY_FORWARD,
                                                new Object[] { crisis.getSupplier().getName(), crisis.getDateFrom(),
                                                                crisis.getDateTo() }))
                                .timestamp(crisis.getActivatedAt())
                                .build();
        }

        private Map<String, String> resolveLatestHashesByProduct(List<CrisisAffectedProduct> associations) {
                Map<String, String> map = new LinkedHashMap<>();
                for (CrisisAffectedProduct association : associations) {
                        Product product = association.getProduct();
                        String hash = ledgerRepository.findLastTransactionByProductId(product.getId())
                                        .map(StockLedger::getCurrentHash)
                                        .orElse("-");
                        map.put(product.getName(), hash);
                }
                return map;
        }

        private List<CrisisAffectedBatchDTO> buildAffectedBatchDetails(List<CrisisAffectedProduct> associations) {
                if (associations.isEmpty()) {
                        return List.of();
                }

                Set<Integer> productIds = associations.stream()
                                .map(ap -> ap.getProduct().getId())
                                .collect(Collectors.toSet());

                Map<Long, ProductBatch> batchesById = new LinkedHashMap<>();
                for (Integer productId : productIds) {
                        for (ProductBatch batch : productBatchService.getAllBatches(productId)) {
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
                                                .remainingQuantity(batch.getRemainingQuantity())
                                                .expired(batch.getExpirationDate() != null
                                                                && batch.getExpirationDate().isBefore(LocalDate.now()))
                                                .depleted(batch.isDepleted())
                                                .build())
                                .toList();
        }

        private void broadcastCrisisNotification(String title, String body, AlertCode code) {
                RoleNotificationMessage message = RoleNotificationMessage.builder()
                                .title(title)
                                .message(body)
                                .code(code)
                                .timestamp(LocalDateTime.now())
                                .build();

                for (Role role : Role.values()) {
                        notificationService.sendNotificationToRole(role, message);
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
