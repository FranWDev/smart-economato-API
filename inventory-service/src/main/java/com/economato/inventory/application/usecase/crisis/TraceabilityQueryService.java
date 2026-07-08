package com.economato.inventory.application.usecase.crisis;

import com.economato.inventory.application.dto.crisis.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.crisis.response.ReverseTraceabilityDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import com.economato.inventory.application.mapper.order.OrderMapper;
import com.economato.inventory.application.mapper.product.ProductBatchMapper;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.ledger.StockLedgerBatchDetail;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraceabilityQueryService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StockLedgerRepository ledgerRepository;
    private final RecipeCookingAuditRepository cookingAuditRepository;
    private final SupplierRepository supplierRepository;
    private final ProductBatchRepository productBatchRepository;
    private final StockLedgerBatchDetailRepository stockLedgerBatchDetailRepository;
    private final OrderMapper orderMapper;
    private final RecipeCookingAuditMapper cookingAuditMapper;
    private final StockLedgerMapper ledgerMapper;
    private final ProductBatchMapper productBatchMapper;
    private final ObjectMapper objectMapper;
    private final I18nService i18nService;

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
                .findByDateRange(from, to).stream()
                .filter(c -> containsAnyProduct(c, productIds))
                .toList();

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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) state.get("components");
        List<Integer> productIds = components.stream()
                .map(comp -> {
                    Object rawId = comp.get("productId");
                    return rawId instanceof Number ? ((Number) rawId).intValue() : null;
                })
                .filter(Objects::nonNull)
                .toList();

        Map<Integer, Product> productsById = productRepository.findAllByIdWithSupplier(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Map<Integer, StockLedger> lastEntradas = ledgerRepository
                .findLastEntradasBeforeDateBatch(productIds, audit.getCookingDate()).stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (StockLedger) row[1]));

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

    public List<RecipeCookingAuditResponseDTO> getCookingAuditsByBatchId(Long batchId) {
        return cookingAuditRepository.findByBatchId(batchId).stream()
                .map(cookingAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private boolean isWithinDateRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null) {
            return false;
        }
        boolean afterFrom = from == null || !value.isBefore(from);
        boolean beforeTo = to == null || !value.isAfter(to);
        return afterFrom && beforeTo;
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

    private boolean containsAnyProduct(RecipeCookingAudit audit, List<Integer> productIds) {
        String componentsState = audit.getComponentsState();
        if (componentsState == null || componentsState.isEmpty() || componentsState.equals("{}")) {
            return false;
        }

        Map<String, Object> state = parseDetails(componentsState);
        if (!state.containsKey("components") || !(state.get("components") instanceof List)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) state.get("components");
        return components.stream().anyMatch(comp -> {
            Object rawId = comp.get("productId");
            Integer pid = rawId instanceof Number ? ((Number) rawId).intValue() : null;
            return pid != null && productIds.contains(pid);
        });
    }
}
