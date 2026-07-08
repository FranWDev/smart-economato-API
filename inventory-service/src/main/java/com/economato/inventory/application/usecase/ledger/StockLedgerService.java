package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.dto.stock.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;

import lombok.extern.slf4j.Slf4j;
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
public class StockLedgerService {

    private final StockMovementRecorder stockMovementRecorder;
    private final StockReversalProcessor stockReversalProcessor;
    private final StockLedgerIntegrityVerifier stockLedgerIntegrityVerifier;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;

    public StockLedgerService(
            StockMovementRecorder stockMovementRecorder,
            StockReversalProcessor stockReversalProcessor,
            StockLedgerIntegrityVerifier stockLedgerIntegrityVerifier,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            SecurityContextHelper securityContextHelper,
            I18nService i18nService) {
        this.stockMovementRecorder = stockMovementRecorder;
        this.stockReversalProcessor = stockReversalProcessor;
        this.stockLedgerIntegrityVerifier = stockLedgerIntegrityVerifier;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.securityContextHelper = securityContextHelper;
        this.i18nService = i18nService;
    }

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

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeStockWithLedger() {
        stockLedgerIntegrityVerifier.synchronizeStockWithLedger();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildAllChains() {
        stockLedgerIntegrityVerifier.rebuildAllChains();
    }
}