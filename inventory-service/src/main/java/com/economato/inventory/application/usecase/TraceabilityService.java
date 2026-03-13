package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.*;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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
                                        .originalAvailabilityPercentage(product.getAvailabilityPercentage())
                                        .build());

                        batchMovements.add(new BatchMovementItem(
                                        product.getId(),
                                        BigDecimal.ZERO,
                                        MovementType.CUARENTENA,
                                        i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE,
                                                        new Object[] { crisisCode })));
                }

                productRepository.updateAvailabilityForProducts(request.getProductIds(), BigDecimal.ZERO);
                crisisAffectedProductRepository.saveAll(affectedProducts);

                List<StockLedger> txs = ledgerService.recordBatchStockMovements(batchMovements, currentUser, null);
                Map<String, String> quarantinedProducts = txs.stream()
                                .collect(Collectors.toMap(tx -> tx.getProduct().getName(),
                                                StockLedger::getCurrentHash));

                List<Order> affectedOrders = orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(
                                request.getSupplierId(), request.getProductIds(), request.getDateFrom(),
                                request.getDateTo());

                List<RecipeCookingAudit> affectedCookings = cookingAuditRepository
                                .findAffectedCookingsByProductIdsAndDateRange(
                                                request.getProductIds(), request.getDateFrom(), request.getDateTo());

                boolean integrityVerified = products.stream()
                                .allMatch(p -> ledgerService.verifyChainIntegrity(p.getId()).isValid());

                broadcastCrisisNotification(
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_TITLE),
                                i18nService.getMessage(MessageKey.CRISIS_ACTIVATION_MESSAGE,
                                                new Object[] { supplier.getName(), quarantinedProducts.keySet(),
                                                                request.getReason() }),
                                AlertCode.FOOD_CRISIS_ACTIVATED);

                meterRegistry.counter("food.crisis.active.count").increment();

                return CrisisResponseDTO.builder()
                                .crisisId(crisisCode)
                                .status("ACTIVE")
                                .reason(request.getReason())
                                .supplierName(supplier.getName())
                                .quarantinedProducts(quarantinedProducts)
                                .affectedOrderIds(
                                                affectedOrders.stream().map(Order::getId).collect(Collectors.toList()))
                                .affectedCookingAuditIds(affectedCookings.stream().map(RecipeCookingAudit::getId)
                                                .collect(Collectors.toList()))
                                .integrityVerified(integrityVerified)
                                .summary(i18nService.getMessage(MessageKey.TRACEABILITY_SUMMARY_FORWARD,
                                                new Object[] { supplier.getName(), request.getDateFrom(),
                                                                request.getDateTo() }))
                                .timestamp(LocalDateTime.now())
                                .build();
        }

        @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
        public void liftCrisis(CrisisLiftRequestDTO request) {
                log.info("Lifting food safety quarantine for products: {}", request.getProductIds());

                List<Product> products = productRepository.findByIdsForUpdate(request.getProductIds());
                User currentUser = securityContextHelper.getCurrentUser();

                List<CrisisAffectedProduct> associations = crisisAffectedProductRepository
                                .findByProductInAndFoodCrisisStatus(products, FoodCrisis.CrisisStatus.ACTIVE);

                Map<Integer, CrisisAffectedProduct> associationMap = associations.stream()
                                .collect(Collectors.toMap(ap -> ap.getProduct().getId(), ap -> ap));

                List<BatchMovementItem> batchMovements = new ArrayList<>();
                Set<FoodCrisis> affectedCrises = new HashSet<>();

                for (Product product : products) {
                        CrisisAffectedProduct association = associationMap.get(product.getId());

                        if (association != null) {
                                product.setAvailabilityPercentage(association.getOriginalAvailabilityPercentage());

                                FoodCrisis crisis = association.getFoodCrisis();
                                crisis.setStatus(FoodCrisis.CrisisStatus.LIFTED);
                                crisis.setLiftedBy(currentUser);
                                crisis.setLiftedAt(LocalDateTime.now());
                                affectedCrises.add(crisis);
                        } else {
                                product.setAvailabilityPercentage(request.getAvailabilityPercentage() != null
                                                ? request.getAvailabilityPercentage()
                                                : BigDecimal.valueOf(100));
                        }

                        batchMovements.add(new BatchMovementItem(
                                        product.getId(),
                                        BigDecimal.ZERO,
                                        MovementType.MODIFICACION,
                                        i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_QUARANTINE_LIFT,
                                                        new Object[] { product.getAvailabilityPercentage() })));
                }

                productRepository.saveAll(products);
                foodCrisisRepository.saveAll(affectedCrises);
                ledgerService.recordBatchStockMovements(batchMovements, currentUser, null);

                broadcastCrisisNotification(
                                i18nService.getMessage(MessageKey.CRISIS_LIFT_TITLE),
                                i18nService.getMessage(MessageKey.CRISIS_LIFT_MESSAGE,
                                                new Object[] { products.stream().map(Product::getName)
                                                                .collect(Collectors.toList()), "RESTORED" }),
                                AlertCode.FOOD_CRISIS_LIFTED);
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

                Map<String, Object> details = parseDetails(audit.getDetails());
                if (details != null && details.containsKey("ingredients")) {
                        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) details.get("ingredients");

                        for (Map<String, Object> ing : ingredients) {
                                Integer productId = (Integer) ing.get("productId");
                                String productName = (String) ing.get("productName");

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
                        return objectMapper.readValue(detailsJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                        log.warn("Failed to parse audit details JSON: {}", detailsJson);
                        return Collections.emptyMap();
                }
        }
}
