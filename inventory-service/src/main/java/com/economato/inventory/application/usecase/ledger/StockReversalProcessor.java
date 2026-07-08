package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.application.dto.stock.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.ledger.StockLedgerBatchDetail;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.domain.stock.PredictorTrigger;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class StockReversalProcessor {

    private final I18nService i18nService;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final SecurityContextHelper securityContextHelper;
    private final StockLedgerBatchDetailRepository batchDetailRepository;
    private final ProductBatchRepository batchRepository;
    private final StockMovementRecorder stockMovementRecorder;
    private final OrderRepository orderRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public StockReversalProcessor(
            I18nService i18nService,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            SecurityContextHelper securityContextHelper,
            StockLedgerBatchDetailRepository batchDetailRepository,
            ProductBatchRepository batchRepository,
            StockMovementRecorder stockMovementRecorder,
            OrderRepository orderRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository) {
        this.i18nService = i18nService;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.securityContextHelper = securityContextHelper;
        this.batchDetailRepository = batchDetailRepository;
        this.batchRepository = batchRepository;
        this.stockMovementRecorder = stockMovementRecorder;
        this.orderRepository = orderRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void revertMovement(String correlationId, String reason) {
        log.info("Iniciando reversión de movimientos: correlationId={}, motivo={}", correlationId, reason);

        List<StockLedger> transactions = ledgerRepository.findByCorrelationId(correlationId);
        if (transactions.isEmpty()) {
            log.warn("No se encontraron transacciones para el correlationId: {}", correlationId);
            return;
        }

        executeReversal(transactions, reason, correlationId);
    }

    public void executeReversal(List<StockLedger> transactions, String reason, String originalCorrelationId) {
        User currentUser = securityContextHelper.getCurrentUser();
        String reversalCorrelationId = "REV-"
                + (originalCorrelationId != null ? originalCorrelationId : UUID.randomUUID().toString());

        if (originalCorrelationId != null) {
            List<StockLedger> existingReversals = ledgerRepository.findByCorrelationId(reversalCorrelationId);
            if (!existingReversals.isEmpty()) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_REVERSION_ALREADY_DONE,
                                new Object[] { originalCorrelationId }));
            }
        }

        List<Long> txIds = transactions.stream().map(StockLedger::getId).toList();

        Map<Long, List<StockLedgerBatchDetail>> detailsByTxId = batchDetailRepository.findByLedgerTransactionIdIn(txIds)
                .stream()
                .collect(Collectors.groupingBy(d -> d.getLedgerTransaction().getId()));

        Map<Long, List<ProductBatch>> createdBatchesByTxId = batchRepository.findByLedgerTransactionIdIn(txIds).stream()
                .collect(Collectors.groupingBy(b -> b.getLedgerTransaction().getId()));

        List<Long> allCreatedBatchIds = createdBatchesByTxId.values().stream()
                .flatMap(List::stream)
                .map(ProductBatch::getId)
                .toList();

        Map<Long, List<StockLedgerBatchDetail>> usageByBatchId = allCreatedBatchIds.isEmpty()
                ? Collections.emptyMap()
                : batchDetailRepository.findByBatchIdIn(allCreatedBatchIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getBatch().getId()));

        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = detailsByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());

            if (details.isEmpty()) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_REVERSION_NO_BATCH_TRACEABILITY,
                                new Object[] { originalTx.getId() }));
            }

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                if (batch == null)
                    continue;

                if (batch.getExpirationDate() != null
                        && batch.getExpirationDate().isBefore(LocalDate.now())) {
                    throw new InvalidOperationException(
                            i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT,
                                    new Object[] { batch.getId(), batch.getProduct().getName(),
                                            batch.getExpirationDate() }));
                }
            }

            List<ProductBatch> createdBatches = createdBatchesByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());
            for (ProductBatch createdBatch : createdBatches) {
                List<StockLedgerBatchDetail> usage = usageByBatchId.getOrDefault(createdBatch.getId(),
                        Collections.emptyList());

                List<StockLedger> externalUsage = usage.stream()
                        .map(StockLedgerBatchDetail::getLedgerTransaction)
                        .filter(tx -> !tx.getId().equals(originalTx.getId()))
                        .toList();

                if (!externalUsage.isEmpty()) {
                    String otherActions = externalUsage.stream()
                            .map(StockLedger::getDescription)
                            .distinct()
                            .collect(Collectors.joining(", "));

                    throw new InvalidOperationException(
                            i18nService.getMessage(MessageKey.ERROR_REVERSION_BATCH_USED,
                                    new Object[] { createdBatch.getId(), otherActions }));
                }
            }
        }

        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = detailsByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                BigDecimal quantityToRestore = detail.getQuantity().negate();

                if (quantityToRestore.compareTo(BigDecimal.ZERO) < 0) {
                    StockSnapshot snapshot = snapshotRepository
                            .findById(originalTx.getProduct().getId()).orElse(null);
                    if (snapshot != null) {
                        BigDecimal resultingStock = snapshot.getCurrentStock().add(quantityToRestore);
                        if (resultingStock.compareTo(BigDecimal.ZERO) < 0) {
                            throw new InvalidOperationException(
                                    i18nService.getMessage(MessageKey.ERROR_REVERSION_NEGATIVE_STOCK,
                                            new Object[] { resultingStock }));
                        }
                    }
                }

                String description = i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_REVERSAL, new Object[] { reason, batch.getId() });

                stockMovementRecorder.recordStockMovementInternal(
                        originalTx.getProduct().getId(),
                        quantityToRestore,
                        MovementType.REVERSION,
                        description,
                        currentUser,
                        originalTx.getOrderId(),
                        batch.getExpirationDate(),
                        reversalCorrelationId,
                        batch.getId(),
                        null,
                        true,
                        false);
            }

            entityManager.flush();
            List<ProductBatch> createdBatches = createdBatchesByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());
            for (ProductBatch createdBatch : createdBatches) {
                log.info("Borrando lote creado por transacción revertida: batchId={}, txId={}",
                        createdBatch.getId(), originalTx.getId());

                batchDetailRepository.deleteAllByBatchId(createdBatch.getId());
                batchRepository.delete(createdBatch);
            }
            entityManager.flush();
        }
    }

    @PredictorTrigger(action = "BATCH_MOVEMENT")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> recordBatchStockMovements(
            List<BatchMovementItem> movements,
            User user,
            Integer orderId,
            boolean enforceReservationGuard) {

        log.info("Iniciando operación batch: {} movimientos", movements.size());

        List<StockLedger> transactions = new ArrayList<>();

        try {
            for (BatchMovementItem item : movements) {
                StockLedger transaction = stockMovementRecorder.recordStockMovementInternal(
                        item.getProductId(),
                        item.getQuantityDelta(),
                        item.getMovementType(),
                        item.getDescription(),
                        user,
                        orderId,
                        item.getExpirationDate(),
                        item.getCorrelationId(),
                        null,
                        null,
                        enforceReservationGuard,
                        false);

                transactions.add(transaction);
            }

            log.info("Operación batch completada exitosamente: {} transacciones registradas",
                    transactions.size());

            return transactions;

        } catch (Exception e) {
            log.debug("Error en operación batch. Revertiendo {} transacciones", transactions.size(), e);
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_BATCH_OPERATION_FAILED, new Object[] { e.getMessage() }));
        }
    }

    @RealtimeSync(entityType = "ledger", action = "UPDATE",
            affectedDomains = {"ledger", "product", "order", "weekly_plan", "stock_alerts"},
            idsFromResult = "productIds")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> processBatchMovements(BatchStockMovementRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();
        String reason = request.getReason() != null ? request.getReason() : "Reversión batch";

        if (request.getOrderId() != null) {
            log.info("Procesando reversión automática de orden: orderId={}", request.getOrderId());
            List<StockLedger> transactions = ledgerRepository.findByOrderId(request.getOrderId());

            if (!transactions.isEmpty()) {
                executeReversal(transactions, reason, null);
            }

            Order order = orderRepository.findByIdWithDetails(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_ORDER_NOT_FOUND)));

            order.setStatus(OrderStatus.CREATED);
            for (var detail : order.getDetails()) {
                detail.setQuantityReceived(null);
            }
            orderRepository.save(order);

            log.info("Reversión de orden completada: orderId={}, estado vuelto a CREATED", request.getOrderId());
            return List.of();
        }

        if (request.getRecipeCookingAuditId() != null) {
            log.info("Procesando reversión automática de cocinado: recipeAuditId={}",
                    request.getRecipeCookingAuditId());
            RecipeCookingAudit audit = recipeCookingAuditRepository
                    .findById(request.getRecipeCookingAuditId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_CULINARY_AUDIT_NOT_FOUND, new Object[] { request.getRecipeCookingAuditId() })));

            if (audit.getCorrelationId() != null) {
                List<StockLedger> transactions = ledgerRepository.findByCorrelationId(audit.getCorrelationId());
                if (!transactions.isEmpty()) {
                    executeReversal(transactions, reason, audit.getCorrelationId());
                }
            }

            recipeCookingAuditRepository.delete(audit);
            log.info("Reversión de cocinado completada: recipeAuditId={}", request.getRecipeCookingAuditId());
            return List.of();
        }

        List<BatchMovementItem> movements = request.getMovements().stream()
                .map(item -> new BatchMovementItem(
                        item.getProductId(),
                        item.getQuantityDelta(),
                        item.getMovementType(),
                        item.getDescription() != null ? item.getDescription() : request.getReason(),
                        item.getExpirationDate()))
                .collect(Collectors.toList());

        return recordBatchStockMovements(movements, currentUser, request.getOrderId(), true);
    }
}
