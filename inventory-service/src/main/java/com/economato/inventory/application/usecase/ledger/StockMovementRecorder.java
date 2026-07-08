package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.dto.ai.BatchConsumptionDetail;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.ledger.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.stock.cache.event.StockMovementEvent;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import com.economato.inventory.application.dto.ai.BatchConsumptionDetail;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.ledger.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.ledger.StockLedgerBatchDetail;
import com.economato.inventory.infrastructure.config.stock.cache.event.StockMovementEvent;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class StockMovementRecorder {

    private final I18nService i18nService;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final ProductBatchService productBatchService;
    private final StockLedgerBatchDetailRepository batchDetailRepository;
    private final ProductBatchRepository batchRepository;
    private final Environment environment;
    private final LedgerProperties ledgerProperties;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WeeklyPlanStockReservationService weeklyPlanStockReservationService;
    private final SecurityContextHelper securityContextHelper;

    private final Counter stockMovementsCounter;
    private final Timer ledgerHashTimer;

    private static final String GENESIS_HASH = "GENESIS";

    public StockMovementRecorder(
            I18nService i18nService,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            ProductBatchService productBatchService,
            StockLedgerBatchDetailRepository batchDetailRepository,
            ProductBatchRepository batchRepository,
            Environment environment,
            LedgerProperties ledgerProperties,
            ApplicationEventPublisher applicationEventPublisher,
            WeeklyPlanStockReservationService weeklyPlanStockReservationService,
            SecurityContextHelper securityContextHelper,
            MeterRegistry meterRegistry) {
        this.i18nService = i18nService;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.productBatchService = productBatchService;
        this.batchDetailRepository = batchDetailRepository;
        this.batchRepository = batchRepository;
        this.environment = environment;
        this.ledgerProperties = ledgerProperties;
        this.applicationEventPublisher = applicationEventPublisher;
        this.weeklyPlanStockReservationService = weeklyPlanStockReservationService;
        this.securityContextHelper = securityContextHelper;

        this.stockMovementsCounter = Counter.builder("stock.ledger.movements.total")
                .description("Total de movimientos en el ledger criptográfico")
                .register(meterRegistry);

        this.ledgerHashTimer = Timer.builder("stock.ledger.hash.duration")
                .description("Latencia del cómputo HMAC-SHA256")
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovementInternal(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId,
            Long targetBatchId,
            String batchCode,
            boolean enforceReservationGuard,
            boolean skipBatchConsumption) {

        log.info("Registrando movimiento: Producto={}, Delta={}, Tipo={}",
                productId, quantityDelta, movementType);

        boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");

        Product product;
        if (isTestProfile) {
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
        } else {
            product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
        }

        StockSnapshot snapshot = snapshotRepository.findById(productId)
                .orElseGet(() -> createInitialSnapshot(product));

        BigDecimal newStock = snapshot.getCurrentStock().add(quantityDelta);

        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_RECIPE_STOCK_INSUFFICIENT,
                            new Object[] { product.getName(), quantityDelta.abs(), snapshot.getCurrentStock() }));
        }

        if (enforceReservationGuard && quantityDelta.compareTo(BigDecimal.ZERO) < 0) {
            weeklyPlanStockReservationService.validateDecrementAgainstActiveReservations(
                productId,
                quantityDelta.abs(),
                snapshot.getCurrentStock());
        }

        List<BatchConsumptionDetail> batchMovements = new ArrayList<>();

        if (!skipBatchConsumption) {
            if (targetBatchId != null) {
                if (quantityDelta.compareTo(BigDecimal.ZERO) < 0) {
                    batchMovements = productBatchService.consumeFromSpecificBatch(targetBatchId, quantityDelta.abs());
                } else if (quantityDelta.compareTo(BigDecimal.ZERO) > 0) {
                    productBatchService.addStockToBatch(targetBatchId, quantityDelta);
                    batchMovements.add(new BatchConsumptionDetail(targetBatchId, quantityDelta.negate()));
                }
            } else if (quantityDelta.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal toConsume = quantityDelta.abs();
                batchMovements = productBatchService.consumeStock(productId, toConsume);
            }
        }

        Optional<StockLedger> lastTransaction = ledgerRepository.findLastTransactionByProductId(productId);
        String previousHash = lastTransaction.map(StockLedger::getCurrentHash).orElse(GENESIS_HASH);
        Long nextSequence = lastTransaction.map(t -> t.getSequenceNumber() + 1).orElse(1L);

        LocalDateTime now = normalizeTimestamp(LocalDateTime.now());

        BigDecimal normalizedDelta = quantityDelta.setScale(3, RoundingMode.HALF_UP);
        BigDecimal normalizedStock = newStock.setScale(3, RoundingMode.HALF_UP);

        String currentHash = calculateTransactionHash(
                productId,
                normalizedDelta,
                normalizedStock,
                movementType,
                description,
                user != null ? user.getId() : null,
                orderId,
                expirationDate,
                correlationId,
                now,
                previousHash,
                nextSequence);

        StockLedger transaction = StockLedger.builder()
                .product(product)
                .quantityDelta(normalizedDelta)
                .resultingStock(normalizedStock)
                .movementType(movementType)
                .description(description)
                .previousHash(previousHash)
                .currentHash(currentHash)
                .transactionTimestamp(now)
                .user(user)
                .orderId(orderId)
                .expirationDate(expirationDate)
                .correlationId(correlationId)
                .sequenceNumber(nextSequence)
                .verified(true)
                .build();

        transaction = ledgerRepository.saveAndFlush(transaction);
        applicationEventPublisher.publishEvent(new NewLedgerTransactionEvent(transaction.getId()));

        if (!skipBatchConsumption && targetBatchId == null
                && quantityDelta.compareTo(BigDecimal.ZERO) > 0) {
            ProductBatch newBatch = productBatchService.createBatch(
                    product,
                    normalizedDelta,
                    expirationDate,
                    transaction,
                    batchCode);
            batchMovements.add(new BatchConsumptionDetail(newBatch.getId(), normalizedDelta.negate()));
        }

        if (!batchMovements.isEmpty() || (skipBatchConsumption && targetBatchId != null)) {
            if (skipBatchConsumption && targetBatchId != null) {
                ProductBatch affectedBatch = batchRepository.findById(targetBatchId)
                        .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
                
                StockLedgerBatchDetail batchDetail = StockLedgerBatchDetail.builder()
                        .ledgerTransaction(transaction)
                        .batch(affectedBatch)
                        .quantity(normalizedDelta)
                        .build();
                batchDetailRepository.save(batchDetail);
            } else {
                List<Long> batchIds = batchMovements.stream()
                        .map(BatchConsumptionDetail::getBatchId)
                        .toList();
                Map<Long, ProductBatch> batchesById = batchRepository.findAllById(batchIds).stream()
                        .collect(Collectors.toMap(ProductBatch::getId, b -> b));

                for (BatchConsumptionDetail detail : batchMovements) {
                    ProductBatch affectedBatch = batchesById.get(detail.getBatchId());
                    if (affectedBatch == null) {
                        throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
                    }

                    StockLedgerBatchDetail batchDetail = StockLedgerBatchDetail.builder()
                            .ledgerTransaction(transaction)
                            .batch(affectedBatch)
                            .quantity(detail.getQuantityConsumed().negate())
                            .build();
                    batchDetailRepository.save(batchDetail);
                }
            }
        }

        stockMovementsCounter.increment();

        snapshot.setCurrentStock(normalizedStock);
        snapshot.setLastTransactionHash(currentHash);
        snapshot.setLastSequenceNumber(nextSequence);
        snapshot.setLastUpdated(now);
        snapshot.setIntegrityStatus("VALID");
        snapshotRepository.save(snapshot);

        product.setCurrentStock(normalizedStock);
        productRepository.save(product);

        applicationEventPublisher.publishEvent(new StockMovementEvent(productId));

        log.info("Movimiento registrado: TX#{} Hash={}", nextSequence, currentHash.substring(0, 8));

        return transaction;
    }

    public String calculateTransactionHash(
            Integer productId,
            BigDecimal quantityDelta,
            BigDecimal resultingStock,
            MovementType movementType,
            String description,
            Integer userId,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId,
            LocalDateTime timestamp,
            String previousHash,
            Long sequenceNumber) {

        return ledgerHashTimer.record(() -> {
            try {
                String data = String.format("%d|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%d",
                        productId,
                        quantityDelta.toPlainString(),
                        resultingStock.toPlainString(),
                        movementType.name(),
                        description != null ? description : "NULL",
                        userId != null ? userId.toString() : "NULL",
                        orderId != null ? orderId.toString() : "NULL",
                        expirationDate != null ? expirationDate.toString() : "NULL",
                        correlationId != null ? correlationId : "NULL",
                        timestamp.toString(),
                        previousHash,
                        sequenceNumber);

                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec(
                        ledgerProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");
                mac.init(secretKey);
                byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

                return HexFormat.of().formatHex(hashBytes);

            } catch (Exception e) {
                throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_STOCK_HASH_CALCULATION), e);
            }
        });
    }

    @RealtimeSync(entityType = "batch", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"batch", "product", "weekly_plan", "ledger", "stock_alerts"})
    @Transactional(rollbackFor = Exception.class)
    public void withdrawExpiredBatch(Long batchId) {
        User user = securityContextHelper.getCurrentUser();
        ProductBatch batch = productBatchService.getBatchById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (batch.isDepleted() || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_DEPLETED_WITHDRAWAL));
        }

        BigDecimal quantity = batch.getRemainingQuantity();
        Integer productId = batch.getProduct().getId();

        productBatchService.depleteExpiredBatch(batchId);

        recordStockMovementInternal(
                productId,
                quantity.negate(),
                MovementType.MERMA,
                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_EXPIRED_BATCH_WITHDRAWAL,
                        new Object[]{batchId}),
                user,
                null,
                null,
                null,
                batchId,
                null,
                false,
                true);

        log.info("Lote caducado retirado: batchId={}, productId={}, qty={}", batchId, productId, quantity);
    }

    public StockSnapshot createInitialSnapshot(Product product) {
        log.info("Creando snapshot inicial para producto {}", product.getId());

        return StockSnapshot.builder()
                .productId(product.getId())
                .product(product)
                .currentStock(product.getCurrentStock())
                .lastTransactionHash(GENESIS_HASH)
                .lastSequenceNumber(0L)
                .lastUpdated(normalizeTimestamp(LocalDateTime.now()))
                .integrityStatus("UNVERIFIED")
                .build();
    }

    public LocalDateTime normalizeTimestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp.truncatedTo(ChronoUnit.MILLIS) : null;
    }
}
