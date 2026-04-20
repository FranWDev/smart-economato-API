package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.cache.event.StockMovementEvent;
import com.economato.inventory.infrastructure.config.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/*
 * SERVICIO DE LEDGER DE STOCK CRIPTOGRÁFICO CON TRAZABILIDAD DE LOTES. NO TOCAR BAJO NINGUN CONCEPTO.
 * Diseñado por: https://github.com/FranWDev
 * 
 * Esta es probablemente el servicio mas critico e importante de todo el sistema, ya que es el encargado de 
 * registrar cada movimiento de stock de forma inmutable y con trazabilidad de lotes.
 * 
 * Implementa un ledger criptográfico donde cada transacción incluye el hash de la anterior, garantizando la integridad de la cadena.
 * 
 * Cada movimiento se registra con aislamiento SERIALIZABLE para evitar condiciones de carrera, y se mantiene un snapshot actualizado para consultas rápidas.
 * 
 * Adicionalmente, se registran detalles de consumo por lote para cada movimiento que afecta stock, lo que permite trazabilidad total y reversión segura de movimientos.
 * 
 * El servicio también expone un método de verificación de integridad que recorre la cadena de transacciones y valida los hashes, detectando cualquier inconsistencia o manipulación.
 * 
 * Se han añadido métricas con Micrometer para monitorear el número de movimientos registrados y la latencia del cálculo de hashes, lo cual 
 * es crítico para detectar posibles problemas de rendimiento, especialmente al usar Virtual Threads.
 * 
 * En resumen, este servicio es el corazón del sistema de inventario, garantizando la precisión, trazabilidad e integridad de todos los movimientos de stock. 
 * Cualquier fallo o inconsistencia aquí podría comprometer toda la gestión de inventario, por lo que se han tomado medidas rigurosas para asegurar su robustez y fiabilidad.
 * 
 * Si eres alguien de los que esta en 2do de DAW y te ha tocado echarle un vistazo a este proyecto (porque los del CIFP virgen de la candelaria quieren cambios)
 * BAJO NINGUN CONCEPTO toques cualquier aspecto de esta clase, ni su lógica, ni su estructura, ni su forma de calcular hashes, ni sus transacciones, ni sus métricas, ni nada.
 * Si rompes algo de aqui, obligas al CIFP a borrar media base de datos.
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class StockLedgerService {

    private final I18nService i18nService;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final ProductBatchService productBatchService;
    private final SecurityContextHelper securityContextHelper;
    private final StockLedgerBatchDetailRepository batchDetailRepository;
    private final ProductBatchRepository batchRepository;
    private final Environment environment;
    private final LedgerProperties ledgerProperties;
        private final BlockchainProperties blockchainProperties;
    private final LedgerMerkleVerificationService ledgerMerkleVerificationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WeeklyPlanStockReservationService weeklyPlanStockReservationService;

    // Métricas declaradas como final para thread-safety
    private final Counter stockMovementsCounter;
    private final Timer ledgerHashTimer;

    private static final String GENESIS_HASH = "GENESIS";

    @PersistenceContext
    private EntityManager entityManager;

    public StockLedgerService(
            I18nService i18nService,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            ProductBatchService productBatchService,
            SecurityContextHelper securityContextHelper,
            StockLedgerBatchDetailRepository batchDetailRepository,
            ProductBatchRepository batchRepository,
            Environment environment,
            LedgerProperties ledgerProperties,
            BlockchainProperties blockchainProperties,
            LedgerMerkleVerificationService ledgerMerkleVerificationService,
            ApplicationEventPublisher applicationEventPublisher,
            WeeklyPlanStockReservationService weeklyPlanStockReservationService,
            MeterRegistry meterRegistry) {
        this.i18nService = i18nService;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.productBatchService = productBatchService;
        this.securityContextHelper = securityContextHelper;
        this.batchDetailRepository = batchDetailRepository;
        this.batchRepository = batchRepository;
        this.environment = environment;
        this.ledgerProperties = ledgerProperties;
        this.blockchainProperties = blockchainProperties;
        this.ledgerMerkleVerificationService = ledgerMerkleVerificationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.weeklyPlanStockReservationService = weeklyPlanStockReservationService;

        // Inicializar métricas
        this.stockMovementsCounter = Counter.builder("stock.ledger.movements.total")
                .description("Total de movimientos en el ledger criptográfico")
                .register(meterRegistry);

        this.ledgerHashTimer = Timer.builder("stock.ledger.hash.duration")
                .description("Latencia del cómputo HMAC-SHA256")
                .publishPercentiles(0.95, 0.99) // Crítico para detectar outliers en Virtual Threads
                .register(meterRegistry);
    }

    /**
     * Registra un movimiento de stock garantizando integridad referencial y
     * cronológica.
     * * Se utiliza Isolation.SERIALIZABLE para evitar el fenómeno de 'Phantom
     * Reads' y
     * asegurar que el cálculo del hash basado en la transacción anterior
     * (previousHash)
     * sea atómico y secuencial, impidiendo que dos hilos generen bifurcaciones en
     * la cadena.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId) {

        return recordStockMovement(productId, quantityDelta, movementType, description, user, orderId, null, null);
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

        return recordStockMovement(productId, quantityDelta, movementType, description, user, orderId, expirationDate,
                null);
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

        return recordStockMovement(productId, quantityDelta, movementType, description, user, orderId, expirationDate,
                correlationId, null);
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

        return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, orderId,
                expirationDate, correlationId, null, batchCode);
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

            return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, orderId,
                expirationDate, correlationId, null, batchCode, enforceReservationGuard);
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

        return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, null,
                expirationDate, null, targetBatchId, null);
    }

    @PredictorTrigger(action = "MANUAL_ADJUSTMENT")
    @RealtimeSync(entityType = "ledger", action = "CREATE",
            affectedDomains = {"ledger", "product", "weekly_plan", "stock_alerts"},
            idsFromResult = "productIds")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger processManualAdjustment(
            com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();

        // Validación: si delta es positivo y no hay batchId, exigir expirationDate
        if (request.getQuantityDelta().compareTo(BigDecimal.ZERO) > 0
                && request.getBatchId() == null
                && request.getExpirationDate() == null) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
        }

        // Validación: expirationDate no puede ser pasada
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
        log.info("Iniciando reversión de movimientos: correlationId={}, motivo={}", correlationId, reason);

        List<StockLedger> transactions = ledgerRepository.findByCorrelationId(correlationId);
        if (transactions.isEmpty()) {
            log.warn("No se encontraron transacciones para el correlationId: {}", correlationId);
            return;
        }

        executeReversal(transactions, reason, correlationId);
    }

    private void executeReversal(List<StockLedger> transactions, String reason, String originalCorrelationId) {
        User currentUser = securityContextHelper.getCurrentUser();
        String reversalCorrelationId = "REV-"
                + (originalCorrelationId != null ? originalCorrelationId : UUID.randomUUID().toString());

        // Prevenir doble reversión si hay correlationId
        if (originalCorrelationId != null) {
            List<StockLedger> existingReversals = ledgerRepository.findByCorrelationId(reversalCorrelationId);
            if (!existingReversals.isEmpty()) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_REVERSION_ALREADY_DONE,
                                new Object[] { originalCorrelationId }));
            }
        }

        List<Long> txIds = transactions.stream().map(StockLedger::getId).toList();

        // FASE 0: Pre-cargar datos
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

        // FASE 1: Verificación de integridad y uso de lotes
        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = detailsByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());

            // Sin trazabilidad de lotes → no se puede revertir de forma segura
            if (details.isEmpty()) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_REVERSION_NO_BATCH_TRACEABILITY,
                                new Object[] { originalTx.getId() }));
            }

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                if (batch == null)
                    continue;

                // Verificar que el lote original no esté caducado
                if (batch.getExpirationDate() != null
                        && batch.getExpirationDate().isBefore(LocalDate.now())) {
                    throw new InvalidOperationException(
                            i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT,
                                    new Object[] { batch.getId(), batch.getProduct().getName(),
                                            batch.getExpirationDate() }));
                }
            }

            // Si la transacción original creó lotes, verificar que no hayan sido usados
            // externamente
            List<ProductBatch> createdBatches = createdBatchesByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());
            for (ProductBatch createdBatch : createdBatches) {
                List<StockLedgerBatchDetail> usage = usageByBatchId.getOrDefault(createdBatch.getId(),
                        Collections.emptyList());

                // Si hay más de un uso, significa que alguien más (receta, ajuste) tocó este
                // lote
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

        // FASE 2: Registro de contra-asientos
        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = detailsByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                BigDecimal quantityToRestore = detail.getQuantity().negate();

                // Validar que la reversión de una ENTRADA (quantityToRestore < 0) no cause
                // stock negativo
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

                // Registrar contra-asiento para el lote específico
                recordStockMovementInternal(
                        originalTx.getProduct().getId(),
                        quantityToRestore,
                        MovementType.REVERSION,
                        description,
                        currentUser,
                        originalTx.getOrderId(),
                        batch.getExpirationDate(),
                        reversalCorrelationId,
                        batch.getId(),
                        null);
            }

            // FASE 3: Eliminación de lotes creados (si aplica)
            entityManager.flush();
            List<ProductBatch> createdBatches = createdBatchesByTxId.getOrDefault(originalTx.getId(),
                    Collections.emptyList());
            for (ProductBatch createdBatch : createdBatches) {
                log.info("Borrando lote creado por transacción revertida: batchId={}, txId={}",
                        createdBatch.getId(), originalTx.getId());

                // Eliminar todos los detalles que vinculan este lote (evita TransientPropertyValueException)
                batchDetailRepository.deleteAllByBatchId(createdBatch.getId());
                batchRepository.delete(createdBatch);
            }
            entityManager.flush();
        }
    }

    private StockLedger recordStockMovementInternal(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            LocalDate expirationDate,
            String correlationId,
            Long targetBatchId,
            String batchCode) {

        return recordStockMovementInternal(
            productId,
            quantityDelta,
            movementType,
            description,
            user,
            orderId,
            expirationDate,
            correlationId,
            targetBatchId,
            batchCode,
            true);
        }

    private StockLedger recordStockMovementInternal(
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
            boolean enforceReservationGuard) {
        return recordStockMovementInternal(productId, quantityDelta, movementType,
                description, user, orderId, expirationDate, correlationId,
                targetBatchId, batchCode, enforceReservationGuard, false);
    }

    private StockLedger recordStockMovementInternal(
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
                // Toda salida de stock DEBE consumir de lotes vía FEFO
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

        // Crear lote automáticamente para cualquier delta positivo sin targetBatchId
        // (incluye REVERSION para que se cree el lote consolidado)
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

        // Guardar detalles de trazabilidad de lotes si existen
        if (!batchMovements.isEmpty() || (skipBatchConsumption && targetBatchId != null)) {
            if (skipBatchConsumption && targetBatchId != null) {
                // Si saltamos el consumo pero pasamos un targetBatchId, registrarlo para trazabilidad
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
                            .quantity(detail.getQuantityConsumed().negate()) // Guardamos la cantidad tal cual afectó al lote
                            .build();
                    batchDetailRepository.save(batchDetail);
                }
            }
        }

        // Incrementar métrica de movimientos totales
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

    /**
     * Genera el sello criptográfico de la transacción.
     * * IMPORTANTE: El orden de los campos en la cadena 'data' es parte del
     * contrato de integridad. Cualquier modificación en el formato
     * String.format(...)
     * invalidará todas las verificaciones de integridad futuras.
     * * @return Hash SHA-256 en formato Hexadecimal.
     */
    private String calculateTransactionHash(
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

    @Transactional(readOnly = true)
    public IntegrityCheckResult verifyChainIntegrity(Integer productId) {
        log.info("Verificando integridad del ledger para producto {}", productId);

        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("Desconocido");

        List<String> merkleErrors = ledgerMerkleVerificationService.verifyLedgerChainIntegrityMerkle(productId);
        List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

        if (merkleErrors.isEmpty()) {
            return new IntegrityCheckResult(productId, productName, true,
                    i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_VALID, new Object[] { chain.size() }),
                    null);
        }

        return new IntegrityCheckResult(productId, productName, false,
                i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_CORRUPTED, new Object[] { merkleErrors.size() }),
                merkleErrors);
    }

    @Transactional(readOnly = true)
    public List<IntegrityCheckResult> verifyChainIntegrityBatch(List<Integer> productIds) {
        log.info("Verificando integridad del ledger para {} productos en batch", productIds.size());

        if (productIds.isEmpty())
            return java.util.Collections.emptyList();

        Map<Integer, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<StockLedger> allChains = ledgerRepository.findByProductIdInOrderBySequenceNumber(productIds);
        Map<Integer, List<StockLedger>> chainsByProduct = allChains.stream()
                .collect(Collectors.groupingBy(l -> l.getProduct().getId()));

        List<IntegrityCheckResult> results = new ArrayList<>();

        for (Integer productId : productIds) {
            Product product = productsById.get(productId);
            String productName = product != null ? product.getName() : "Desconocido";
            List<StockLedger> chain = chainsByProduct.getOrDefault(productId, java.util.Collections.emptyList());

            if (chain.isEmpty()) {
                results.add(new IntegrityCheckResult(productId, productName, true,
                        i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_NO_TRANSACTIONS),
                        null));
                continue;
            }

            List<String> errors = new ArrayList<>();
            String expectedPreviousHash = GENESIS_HASH;

            for (int i = 0; i < chain.size(); i++) {
                StockLedger tx = chain.get(i);

                if (!tx.getPreviousHash().equals(expectedPreviousHash)) {
                    String error = i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_PREVIOUS_HASH_MISMATCH,
                            new Object[] {
                                    tx.getSequenceNumber(),
                                    expectedPreviousHash.substring(0, Math.min(8, expectedPreviousHash.length())),
                                    tx.getPreviousHash().substring(0, Math.min(8, tx.getPreviousHash().length()))
                            });
                    errors.add(error);
                }

                BigDecimal normalizedDelta = tx.getQuantityDelta().setScale(3, java.math.RoundingMode.HALF_UP);
                BigDecimal normalizedStock = tx.getResultingStock().setScale(3, java.math.RoundingMode.HALF_UP);
                LocalDateTime normalizedTimestamp = normalizeTimestamp(tx.getTransactionTimestamp());

                String recalculatedHash = calculateTransactionHash(
                        productId,
                        normalizedDelta,
                        normalizedStock,
                        tx.getMovementType(),
                        tx.getDescription(),
                        tx.getUser() != null ? tx.getUser().getId() : null,
                        tx.getOrderId(),
                        tx.getExpirationDate(),
                        tx.getCorrelationId(),
                        normalizedTimestamp,
                        tx.getPreviousHash(),
                        tx.getSequenceNumber());

                if (!recalculatedHash.equals(tx.getCurrentHash())) {
                    String error = i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_HASH_CORRUPT,
                            new Object[] {
                                    tx.getSequenceNumber(),
                                    recalculatedHash.substring(0, Math.min(8, recalculatedHash.length())),
                                    tx.getCurrentHash().substring(0, Math.min(8, tx.getCurrentHash().length())),
                                    normalizedDelta,
                                    normalizedStock
                            });
                    errors.add(error);
                }

                if (tx.getSequenceNumber() != (i + 1L)) {
                    String error = i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_SEQUENCE_BROKEN,
                            new Object[] { tx.getSequenceNumber(), (i + 1L) });
                    errors.add(error);
                }

                expectedPreviousHash = tx.getCurrentHash();
            }

            if (errors.isEmpty()) {
                results.add(new IntegrityCheckResult(productId, productName, true,
                        i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_VALID, new Object[] { chain.size() }),
                        null));
            } else {
                results.add(new IntegrityCheckResult(productId, productName, false,
                        i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_CORRUPTED, new Object[] { errors.size() }),
                        errors));
            }
        }
        return results;
    }

    @Transactional
    public List<IntegrityCheckResult> verifyAllChains() {
        log.info("Verificando integridad de todas las cadenas...");

        List<StockSnapshot> snapshots = snapshotRepository.findAll();
        List<Integer> productIds = snapshots.stream().map(StockSnapshot::getProductId).toList();

        List<IntegrityCheckResult> results = verifyChainIntegrityBatch(productIds);

        Map<Integer, IntegrityCheckResult> resultsByProduct = results.stream()
                .collect(Collectors.toMap(IntegrityCheckResult::getProductId, r -> r));

        for (StockSnapshot snapshot : snapshots) {
            IntegrityCheckResult result = resultsByProduct.get(snapshot.getProductId());
            if (result != null) {
                snapshot.setIntegrityStatus(result.isValid() ? "VALID" : "CORRUPTED");
                snapshot.setLastVerified(LocalDateTime.now());
            }
        }
        snapshotRepository.saveAll(snapshots);

        long validChains = results.stream().filter(IntegrityCheckResult::isValid).count();
        log.info("Verificación completa: {}/{} cadenas íntegras", validChains, results.size());

        return results;
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

    private StockSnapshot createInitialSnapshot(Product product) {
        log.info("Creando snapshot inicial para producto {}", product.getId());

        return StockSnapshot.builder()
                .productId(product.getId())
                .product(product)
                .currentStock(product.getCurrentStock())
                .lastTransactionHash(GENESIS_HASH)
                .lastSequenceNumber(0L)
                .lastUpdated(LocalDateTime.now())
                .integrityStatus("UNVERIFIED")
                .build();
    }

    private LocalDateTime normalizeTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }

    @PredictorTrigger(action = "BATCH_MOVEMENT")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> recordBatchStockMovements(
            List<BatchMovementItem> movements,
            User user,
            Integer orderId) {

        return recordBatchStockMovements(movements, user, orderId, true);
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
                StockLedger transaction = recordStockMovementInternal(
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
                        enforceReservationGuard);

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

            order.setStatus(com.economato.inventory.domain.model.OrderStatus.CREATED);
            for (var detail : order.getDetails()) {
                detail.setQuantityReceived(null);
            }
            orderRepository.save(order);

            log.info("Reversión de orden completada: orderId={}, estado vuelto a CREATED", request.getOrderId());
            return List.of(); // En este caso no devolvemos los nuevos movimientos de la reversión para no
                              // confundir al DTO
        }

        if (request.getRecipeCookingAuditId() != null) {
            log.info("Procesando reversión automática de cocinado: recipeAuditId={}",
                    request.getRecipeCookingAuditId());
            com.economato.inventory.domain.model.RecipeCookingAudit audit = recipeCookingAuditRepository
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

        // Lógica original para compatibilidad
        List<BatchMovementItem> movements = request.getMovements().stream()
                .map(item -> new BatchMovementItem(
                        item.getProductId(),
                        item.getQuantityDelta(),
                        item.getMovementType(),
                        item.getDescription() != null ? item.getDescription() : request.getReason(),
                        item.getExpirationDate()))
                .collect(Collectors.toList());

        return recordBatchStockMovements(movements, currentUser, request.getOrderId());
    }

    /**
     * Obtiene la lista de IDs de productos que tienen historial de ledger.
     * Solo devuelve productos que tienen al menos una transacción registrada.
     * 
     */
    @Transactional(readOnly = true)
    public List<Integer> getProductsWithLedger() {
        log.debug("Obteniendo productos con historial de ledger");
        return ledgerRepository.findDistinctProductIds();
    }

    /**
     * Verifica la integridad de las cadenas de todos los productos que tienen
     * ledger.
     * A diferencia de verifyAllChains(), este método solo verifica productos con
     * transacciones,
     * evitando procesar productos sin historial.
     * 
     */
    @Transactional(readOnly = true)
    public List<IntegrityCheckResult> verifyProductsWithLedger() {
        log.info("Verificando integridad de productos con ledger...");

        List<Integer> productIds = getProductsWithLedger();
        return verifyChainIntegrityBatch(productIds);
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

        // 1. Agotar el lote directamente (valida internamente que esté caducado)
        productBatchService.depleteExpiredBatch(batchId);

        // 2. Registrar movimiento en el ledger SIN targetBatchId
        //    (el lote ya fue agotado en el paso anterior, no necesita consumeFromSpecificBatch)
        recordStockMovementInternal(
                productId,
                quantity.negate(),
                MovementType.MERMA,
                i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_EXPIRED_BATCH_WITHDRAWAL,
                        new Object[]{batchId}),
                user,
                null,   // orderId
                null,   // expirationDate
                null,   // correlationId
                batchId, // targetBatchId (para trazabilidad en el ledger)
                null,   // batchCode
                false,  // enforceReservationGuard
                true);  // skipBatchConsumption = true (lote ya agotado por depleteExpiredBatch)

        log.info("Lote caducado retirado: batchId={}, productId={}, qty={}", batchId, productId, quantity);
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    public void synchronizeStockWithLedger() {
        log.info("Iniciando sincronización exhaustiva de stock con el ledger...");
        List<Product> products = productRepository.findAll();
        List<Product> productsToSave = new ArrayList<>();
        List<StockSnapshot> snapshotsToSave = new ArrayList<>();
        List<ProductBatch> batchesToSave = new ArrayList<>();

        for (Product product : products) {
            // Buscamos la última realidad en el ledger
            Optional<StockLedger> lastTx = ledgerRepository.findLastTransactionByProductId(product.getId());
            BigDecimal realStock = lastTx.map(StockLedger::getResultingStock).orElse(BigDecimal.ZERO);
            String lastHash = lastTx.map(StockLedger::getCurrentHash).orElse(GENESIS_HASH);
            Long lastSeq = lastTx.map(StockLedger::getSequenceNumber).orElse(0L);

            // 1. Sincronizar Producto
            if (product.getCurrentStock().compareTo(realStock) != 0) {
                log.info("Corrigiendo stock de producto {}: {} -> {}", product.getId(), product.getCurrentStock(), realStock);
                product.setCurrentStock(realStock);
                productsToSave.add(product);
            }

            // 2. Sincronizar Snapshot (Stock + Hash + Secuencia)
            // Es vital actualizar el hash/secuencia para no romper la cadena en el siguiente movimiento
            StockSnapshot snapshot = snapshotRepository.findById(product.getId())
                    .orElseGet(() -> createInitialSnapshot(product));
            
            boolean snapshotChanged = false;
            if (snapshot.getCurrentStock().compareTo(realStock) != 0) {
                snapshot.setCurrentStock(realStock);
                snapshotChanged = true;
            }
            if (!lastHash.equals(snapshot.getLastTransactionHash())) {
                snapshot.setLastTransactionHash(lastHash);
                snapshotChanged = true;
            }
            if (!lastSeq.equals(snapshot.getLastSequenceNumber())) {
                snapshot.setLastSequenceNumber(lastSeq);
                snapshotChanged = true;
            }

            if (snapshotChanged) {
                log.info("Actualizando snapshot de producto {}: stock={}, sequence={}, hash={}", 
                        product.getId(), realStock, lastSeq, lastHash);
                snapshot.setLastUpdated(LocalDateTime.now());
                snapshot.setIntegrityStatus("VERIFIED");
                snapshotsToSave.add(snapshot);
            }

            // 3. Limpiar Lotes Huérfanos
            // Si el ledger dice que hay 0, no puede haber lotes con stock positivo
            if (realStock.compareTo(BigDecimal.ZERO) == 0) {
                List<ProductBatch> activeBatches = batchRepository.findActiveByProductIdOrderByExpiration(product.getId());
                for (ProductBatch b : activeBatches) {
                    if (b.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0 || !b.isDepleted()) {
                        log.info("Limpiando lote huérfano {}: remaining {} -> 0", b.getId(), b.getRemainingQuantity());
                        b.setRemainingQuantity(BigDecimal.ZERO);
                        b.setDepleted(true);
                        batchesToSave.add(b);
                    }
                }
            }
        }

        if (!productsToSave.isEmpty()) productRepository.saveAll(productsToSave);
        if (!snapshotsToSave.isEmpty()) snapshotRepository.saveAll(snapshotsToSave);
        if (!batchesToSave.isEmpty()) batchRepository.saveAll(batchesToSave);

        log.info("Sincronización exhaustiva completada. Se han corregido {} productos, {} snapshots y {} lotes.", 
                productsToSave.size(), snapshotsToSave.size(), batchesToSave.size());
    }

    /**
     * RECONSTRUCCIÓN TOTAL DE LA BLOCKCHAIN (SALVACIÓN PROFUNDA)
     * Este método recalcula TODA la cadena de transacciones para cada producto, 
     * empezando desde stock 0 en la transacción #1.
     * 
     * Corrige el error de "herencia de stock sucio" que ocurre al resetear el ledger 
     * sin resetear las tablas de Producto.
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "products_page", "product", "product_stats", "products_search", "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    public void rebuildAllChains() {
        log.warn("INICIANDO RECONSTRUCCIÓN TOTAL DE BLOQUES Y HASHES DEL LEDGER...");
        
        List<Product> products = productRepository.findAll();
        List<StockSnapshot> snapshotsToUpdate = new ArrayList<>();
        List<Product> productsToUpdate = new ArrayList<>();

        for (Product product : products) {
            List<StockLedger> transactions = ledgerRepository.findByProductIdOrderBySequenceNumber(product.getId());
            if (transactions.isEmpty()) continue;

            log.info("Recalculando cadena para Producto {}: {} transacciones", product.getId(), transactions.size());

            BigDecimal currentStock = BigDecimal.ZERO;
            String previousHash = GENESIS_HASH;

            for (StockLedger tx : transactions) {
                BigDecimal delta = tx.getQuantityDelta();
                BigDecimal newResultingStock = currentStock.add(delta).setScale(3, RoundingMode.HALF_UP);

                // Recalculamos el hash con los nuevos datos "limpios"
                String newHash = calculateTransactionHash(
                    product.getId(),
                    delta,
                    newResultingStock,
                    tx.getMovementType(),
                    tx.getDescription(),
                    tx.getUser() != null ? tx.getUser().getId() : null,
                    tx.getOrderId(),
                    tx.getExpirationDate(),
                    tx.getCorrelationId(),
                    tx.getTransactionTimestamp(),
                    previousHash,
                    tx.getSequenceNumber()
                );

                // Actualizamos la transacción
                tx.setResultingStock(newResultingStock);
                tx.setPreviousHash(previousHash);
                tx.setCurrentHash(newHash);
                tx.setVerified(true);

                currentStock = newResultingStock;
                previousHash = newHash;
            }

            // Guardamos la cadena corregida para este producto
            ledgerRepository.saveAll(transactions);
            ledgerRepository.flush();

            // Actualizamos la tabla de Producto con el final de la cadena limpia
            product.setCurrentStock(currentStock);
            productsToUpdate.add(product);

            // Actualizamos o creamos el Snapshot
            StockSnapshot snapshot = snapshotRepository.findById(product.getId())
                    .orElseGet(() -> createInitialSnapshot(product));
            
            snapshot.setCurrentStock(currentStock);
            snapshot.setLastTransactionHash(previousHash);
            snapshot.setLastSequenceNumber((long) transactions.size());
            snapshot.setLastUpdated(LocalDateTime.now());
            snapshot.setIntegrityStatus("VALID");
            snapshotsToUpdate.add(snapshot);
            
            // Limpiar lotes si el stock final es 0
            if (currentStock.compareTo(BigDecimal.ZERO) == 0) {
                List<ProductBatch> activeBatches = batchRepository.findActiveByProductIdOrderByExpiration(product.getId());
                for (ProductBatch b : activeBatches) {
                    b.setRemainingQuantity(BigDecimal.ZERO);
                    b.setDepleted(true);
                }
                batchRepository.saveAll(activeBatches);
            }
        }

        productRepository.saveAll(productsToUpdate);
        snapshotRepository.saveAll(snapshotsToUpdate);

        log.info("RECONSTRUCCIÓN COMPLETADA: {} productos procesados.", productsToUpdate.size());
    }
}
