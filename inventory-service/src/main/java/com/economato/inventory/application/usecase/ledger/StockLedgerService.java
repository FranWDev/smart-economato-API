package com.economato.inventory.application.usecase.ledger;

import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.dto.stock.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResponseDTO;
import com.economato.inventory.application.dto.stock.response.StockSnapshotResponseDTO;
import com.economato.inventory.application.dto.stock.response.BatchStockMovementResponseDTO;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BatchMovementException;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Facade y orquestador del Ledger de Stock.
 * Delega sus responsabilidades de escritura, reversión y verificación
 * a componentes especializados y cohesivos.
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class StockLedgerService {

    private final StockMovementRecorder stockMovementRecorder;
    private final StockReversalProcessor stockReversalProcessor;
    private final StockLedgerIntegrityVerifier stockLedgerIntegrityVerifier;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;
    private final StockLedgerMapper stockLedgerMapper;

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, orderId, null, null, null, null, true, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, orderId, expirationDate, null, null, null, true, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, orderId, expirationDate, correlationId, null, null, true, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId,
            String batchCode) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, orderId, expirationDate, correlationId, null, batchCode, true, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId,
            String batchCode,
            boolean enforceReservationGuard) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, orderId, expirationDate, correlationId, null, batchCode, enforceReservationGuard, false);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordManualAdjustment(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Long targetBatchId,
            LocalDate expirationDate) {
        return stockMovementRecorder.recordStockMovementInternal(
                productId, quantityDelta, movementType, description, user, null, expirationDate, null, targetBatchId, null, true, false);
    }

    @PredictorTrigger(action = "MANUAL_ADJUSTMENT")
    @RealtimeSync(entityType = "ledger", action = "CREATE",
            affectedDomains = {"ledger", "product", "weekly_plan", "stock_alerts"},
            idsFromResult = "productIds")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger processManualAdjustment(ManualStockAdjustmentRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();

        if (request.getQuantityDelta().compareTo(BigDecimal.ZERO) > 0
                && request.getBatchId() == null
                && request.getExpirationDate() == null) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
        }

        if (request.getExpirationDate() != null
                && request.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST,
                            new Object[] { request.getExpirationDate() }));
        }

        return recordManualAdjustment(
                request.getProductId(),
                request.getQuantityDelta(),
                request.getMovementType(),
                request.getDescription(),
                currentUser,
                request.getBatchId(),
                request.getExpirationDate());
    }

    @PredictorTrigger(action = "REVERSION")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void revertMovement(String correlationId, String reason) {
        stockReversalProcessor.revertMovement(correlationId, reason);
    }

    @Transactional(readOnly = true)
    public IntegrityCheckResult verifyChainIntegrity(Integer productId) {
        return stockLedgerIntegrityVerifier.verifyChainIntegrity(productId);
    }

    @Transactional(readOnly = true)
    public List<IntegrityCheckResult> verifyChainIntegrityBatch(List<Integer> productIds) {
        return stockLedgerIntegrityVerifier.verifyChainIntegrityBatch(productIds);
    }

    @Transactional
    public List<IntegrityCheckResult> verifyAllChains() {
        return stockLedgerIntegrityVerifier.verifyAllChains();
    }

    @Transactional(readOnly = true)
    public List<StockLedger> getProductHistory(Integer productId) {
        return ledgerRepository.findByProductIdOrderBySequenceNumber(productId);
    }

    @Transactional(readOnly = true)
    public Page<StockLedger> getProductHistory(Integer productId, Pageable pageable) {
        return ledgerRepository.findByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<StockSnapshot> getCurrentStock(Integer productId) {
        return snapshotRepository.findByIdWithProduct(productId);
    }

    @PredictorTrigger(action = "BATCH_MOVEMENT")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> recordBatchStockMovements(
            List<BatchMovementItem> movements,
            User user,
            Integer orderId) {
        return stockReversalProcessor.recordBatchStockMovements(movements, user, orderId, true);
    }

    @PredictorTrigger(action = "BATCH_MOVEMENT")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> recordBatchStockMovements(
            List<BatchMovementItem> movements,
            User user,
            Integer orderId,
            boolean enforceReservationGuard) {
        return stockReversalProcessor.recordBatchStockMovements(movements, user, orderId, enforceReservationGuard);
    }

    @RealtimeSync(entityType = "ledger", action = "UPDATE",
            affectedDomains = {"ledger", "product", "order", "weekly_plan", "stock_alerts"},
            idsFromResult = "productIds")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> processBatchMovements(BatchStockMovementRequestDTO request) {
        return stockReversalProcessor.processBatchMovements(request);
    }

    @Transactional(readOnly = true)
    public List<Integer> getProductsWithLedger() {
        return ledgerRepository.findDistinctProductIds();
    }

    @Transactional(readOnly = true)
    public List<IntegrityCheckResult> verifyProductsWithLedger() {
        return verifyChainIntegrityBatch(getProductsWithLedger());
    }

    @Transactional(readOnly = true)
    public ProductConsumptionResponseDTO getProductConsumption(Integer productId, LocalDateTime startDate,
            LocalDateTime endDate) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_CONSUMPTION_PRODUCT_NOT_FOUND,
                                new Object[] { productId })));

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_CONSUMPTION_INVALID_DATE_RANGE));
        }

        List<Object[]> results = ledgerRepository.getConsumptionByProductIdAndDateRange(productId, startDate, endDate);

        List<ProductConsumptionResponseDTO.DailyConsumptionDTO> breakdown = results.stream()
                .map(row -> {
                    Date sqlDate = (Date) row[0];
                    BigDecimal consumed = (BigDecimal) row[1];
                    return new ProductConsumptionResponseDTO.DailyConsumptionDTO(sqlDate.toLocalDate(), consumed);
                })
                .collect(Collectors.toList());

        return ProductConsumptionResponseDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .breakdown(breakdown)
                .unit(product.getUnit())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<DailyConsumptionDTO>> getDailyConsumptionBatch(
            List<Integer> productIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        
        List<Object[]> results = ledgerRepository.getConsumptionByProductIdsAndDateRange(productIds, startDate, endDate);
        
        Map<Integer, List<DailyConsumptionDTO>> breakdownByProduct = new HashMap<>();
        for (Object[] row : results) {
            Integer productId = (Integer) row[0];
            Date sqlDate = (Date) row[1];
            BigDecimal consumed = (BigDecimal) row[2];
            
            breakdownByProduct.computeIfAbsent(productId, k -> new ArrayList<>())
                    .add(new DailyConsumptionDTO(sqlDate.toLocalDate(), consumed));
        }
        return breakdownByProduct;
    }

    @Transactional(readOnly = true)
    public Map<Integer, ProductConsumptionResponseDTO> getProductConsumptionBatch(List<Integer> productIds, LocalDateTime startDate,
            LocalDateTime endDate) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        
        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_CONSUMPTION_INVALID_DATE_RANGE));
        }

        Map<Integer, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Map<Integer, List<DailyConsumptionDTO>> breakdownByProduct = getDailyConsumptionBatch(productIds, startDate, endDate);

        return productIds.stream()
                .filter(products::containsKey)
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            Product p = products.get(id);
                            return ProductConsumptionResponseDTO.builder()
                                    .productId(id)
                                    .productName(p.getName())
                                    .breakdown(breakdownByProduct.getOrDefault(id, Collections.emptyList()))
                                    .unit(p.getUnit())
                                    .startDate(startDate)
                                    .endDate(endDate)
                                    .build();
                        }
                ));
    }

    @RealtimeSync(entityType = "batch", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"batch", "product", "weekly_plan", "ledger", "stock_alerts"})
    @Transactional(rollbackFor = Exception.class)
    public void withdrawExpiredBatch(Long batchId) {
        stockMovementRecorder.withdrawExpiredBatch(batchId);
    }

    @RealtimeSync(entityType = "batch", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"batch", "product", "weekly_plan", "ledger", "stock_alerts"})
    @Transactional(rollbackFor = Exception.class)
    public Void withdrawExpiredBatchEntity(Long batchId) {
        stockMovementRecorder.withdrawExpiredBatch(batchId);
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeStockWithLedger() {
        stockLedgerIntegrityVerifier.synchronizeStockWithLedger();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildAllChains() {
        stockLedgerIntegrityVerifier.rebuildAllChains();
    }

    @Transactional(readOnly = true)
    public Page<StockLedgerResponseDTO> getProductHistoryDto(Integer productId, Pageable pageable) {
        return getProductHistory(productId, pageable).map(stockLedgerMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public IntegrityCheckResponseDTO verifyProductIntegrityDto(Integer productId) {
        IntegrityCheckResult result = verifyChainIntegrity(productId);
        List<StockLedger> history = getProductHistory(productId);
        return IntegrityCheckResponseDTO.builder()
                .productId(result.getProductId())
                .productName(result.getProductName())
                .valid(result.isValid())
                .message(result.getMessage())
                .errors(result.getErrors())
                .totalTransactions(history.size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<IntegrityCheckResponseDTO> verifyAllChainsDto() {
        List<IntegrityCheckResult> results = verifyAllChains();
        return results.stream()
                .map(result -> IntegrityCheckResponseDTO.builder()
                        .valid(result.isValid())
                        .message(result.getMessage())
                        .errors(result.getErrors())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StockSnapshotResponseDTO getCurrentStockDto(Integer productId) {
        StockSnapshot snapshot = getCurrentStock(productId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
        return StockSnapshotResponseDTO.builder()
                .productId(snapshot.getProductId())
                .productName(snapshot.getProduct().getName())
                .currentStock(snapshot.getCurrentStock())
                .lastTransactionHash(snapshot.getLastTransactionHash())
                .lastSequenceNumber(snapshot.getLastSequenceNumber())
                .lastUpdated(snapshot.getLastUpdated())
                .lastVerified(snapshot.getLastVerified())
                .integrityStatus(snapshot.getIntegrityStatus())
                .build();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedgerResponseDTO registerManualAdjustmentDto(ManualStockAdjustmentRequestDTO request) {
        StockLedger transaction = processManualAdjustment(request);
        return stockLedgerMapper.toDTO(transaction);
    }

    @Transactional(readOnly = true)
    public ProductConsumptionResponseDTO getProductConsumptionDto(Integer productId, LocalDate date, Integer lastDays, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start;
        LocalDateTime end;

        if (date != null) {
            start = date.atStartOfDay();
            end = date.atTime(23, 59, 59, 999999999);
        } else if (lastDays != null) {
            end = LocalDateTime.now();
            start = end.minusDays(lastDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (startDate != null && endDate != null) {
            start = startDate;
            end = endDate;
        } else {
            start = LocalDate.now().atStartOfDay();
            end = LocalDateTime.now();
        }

        return getProductConsumption(productId, start, end);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public BatchStockMovementResponseDTO processBatchMovementsDto(BatchStockMovementRequestDTO request) {
        try {
            List<StockLedger> transactions = processBatchMovements(request);
            return BatchStockMovementResponseDTO.builder()
                    .success(true)
                    .processedCount(transactions.size())
                    .totalCount(request.getMovements().size())
                    .message(i18nService.getMessage(MessageKey.SUCCESS_BATCH_MOVEMENT, new Object[]{transactions.size()}))
                    .transactions(transactions.stream()
                            .map(stockLedgerMapper::toDTO)
                            .collect(Collectors.toList()))
                    .build();
        } catch (Exception e) {
            BatchStockMovementResponseDTO errorResponse = BatchStockMovementResponseDTO.builder()
                    .success(false)
                    .processedCount(0)
                    .totalCount(request.getMovements().size())
                    .message(i18nService.getMessage(MessageKey.ERROR_BATCH_OPERATION_REVERTED))
                    .errorDetail(e.getMessage())
                    .build();
            throw new BatchMovementException(errorResponse, e.getMessage());
        }
    }
}