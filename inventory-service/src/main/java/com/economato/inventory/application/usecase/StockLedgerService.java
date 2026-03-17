package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Order;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockLedgerBatchDetail;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

        // Inicializar métricas
        this.stockMovementsCounter = Counter.builder("stock.ledger.movements.total")
                .description("Total de movimientos en el ledger criptográfico")
                .register(meterRegistry);

        this.ledgerHashTimer = Timer.builder("stock.ledger.hash.duration")
                .description("Latencia del cómputo SHA-256")
                .publishPercentiles(0.95, 0.99) // Crítico para detectar outliers en Virtual Threads
                .register(meterRegistry);
    }

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
            java.time.LocalDate expirationDate) {

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
            java.time.LocalDate expirationDate,
            String correlationId) {

        return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, orderId,
                expirationDate, correlationId, null);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordManualAdjustment(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Long targetBatchId,
            java.time.LocalDate expirationDate) {

        return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, null, expirationDate, null, targetBatchId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger processManualAdjustment(com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO request) {
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
                && request.getExpirationDate().isBefore(java.time.LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST,
                        new Object[]{request.getExpirationDate()}));
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
        String reversalCorrelationId = "REV-" + (originalCorrelationId != null ? originalCorrelationId : java.util.UUID.randomUUID().toString());

        // Prevenir doble reversión si hay correlationId
        if (originalCorrelationId != null) {
            List<StockLedger> existingReversals = ledgerRepository.findByCorrelationId(reversalCorrelationId);
            if (!existingReversals.isEmpty()) {
                throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_REVERSION_ALREADY_DONE, new Object[]{originalCorrelationId}));
            }
        }

        // FASE 1: Verificación de integridad y uso de lotes
        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = batchDetailRepository.findByLedgerTransactionId(originalTx.getId());

            // Sin trazabilidad de lotes → no se puede revertir de forma segura
            if (details.isEmpty()) {
                throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_REVERSION_NO_BATCH_TRACEABILITY,
                            new Object[]{originalTx.getId()}));
            }

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                if (batch == null) continue;

                // Verificar que el lote original no esté caducado
                if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(java.time.LocalDate.now())) {
                    throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT,
                                new Object[]{batch.getId(), batch.getProduct().getName(), batch.getExpirationDate()}));
                }
            }

            // Si la transacción original creó lotes, verificar que no hayan sido usados externamente
            List<ProductBatch> createdBatches = batchRepository.findByLedgerTransactionId(originalTx.getId());
            for (ProductBatch createdBatch : createdBatches) {
                List<StockLedgerBatchDetail> usage = batchDetailRepository.findByBatchId(createdBatch.getId());
                
                // Si hay más de un uso, significa que alguien más (receta, ajuste) tocó este lote
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
                            "No se puede revertir la entrada: el lote #" + createdBatch.getId() +
                                    " ya ha sido utilizado en: " + otherActions);
                }
            }
        }

        // FASE 2: Registro de contra-asientos
        for (StockLedger originalTx : transactions) {
            List<StockLedgerBatchDetail> details = batchDetailRepository.findByLedgerTransactionId(originalTx.getId());

            for (StockLedgerBatchDetail detail : details) {
                ProductBatch batch = detail.getBatch();
                BigDecimal quantityToRestore = detail.getQuantity().negate();
                
                // Validar que la reversión de una ENTRADA (quantityToRestore < 0) no cause stock negativo
                if (quantityToRestore.compareTo(BigDecimal.ZERO) < 0) {
                    com.economato.inventory.domain.model.StockSnapshot snapshot =
                            snapshotRepository.findById(originalTx.getProduct().getId()).orElse(null);
                    if (snapshot != null) {
                        BigDecimal resultingStock = snapshot.getCurrentStock().add(quantityToRestore);
                        if (resultingStock.compareTo(BigDecimal.ZERO) < 0) {
                            throw new InvalidOperationException(
                                "No se puede deshacer la entrada: resultaría en stock negativo (" + resultingStock + ").");
                        }
                    }
                }

                String description = "REVERSIÓN: " + reason + " [lote original: " + batch.getId() + "]";

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
                        batch.getId()); 
            }

            // FASE 3: Eliminación de lotes creados (si aplica)
            entityManager.flush();
            List<ProductBatch> createdBatches = batchRepository.findByLedgerTransactionId(originalTx.getId());
            for (ProductBatch createdBatch : createdBatches) {
                log.info("Borrando lote creado por transacción revertida: batchId={}, txId={}",
                        createdBatch.getId(), originalTx.getId());
                
                List<StockLedgerBatchDetail> usage = batchDetailRepository.findByBatchId(createdBatch.getId());
                batchDetailRepository.deleteAll(usage);
                batchRepository.delete(createdBatch);
            }
        }
    }

    private StockLedger recordStockMovementInternal(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId,
            java.time.LocalDate expirationDate,
            String correlationId,
            Long targetBatchId) {

        log.info("Registrando movimiento: Producto={}, Delta={}, Tipo={}",
                productId, quantityDelta, movementType);

        boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");

        Product product;
        if (isTestProfile) {
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new InvalidOperationException("Producto no encontrado: " + productId));
        } else {
            product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new InvalidOperationException("Producto no encontrado: " + productId));
        }

        StockSnapshot snapshot = snapshotRepository.findById(productId)
                .orElseGet(() -> createInitialSnapshot(product));

        BigDecimal newStock = snapshot.getCurrentStock().add(quantityDelta);

        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_RECIPE_STOCK_INSUFFICIENT,
                            new Object[] { product.getName(), quantityDelta.abs(), snapshot.getCurrentStock() }));
        }

        List<BatchConsumptionDetail> batchMovements = new ArrayList<>();
        
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

        Optional<StockLedger> lastTransaction = ledgerRepository.findLastTransactionByProductId(productId);
        String previousHash = lastTransaction.map(StockLedger::getCurrentHash).orElse(GENESIS_HASH);
        Long nextSequence = lastTransaction.map(t -> t.getSequenceNumber() + 1).orElse(1L);

        LocalDateTime now = normalizeTimestamp(LocalDateTime.now());

        BigDecimal normalizedDelta = quantityDelta.setScale(3, java.math.RoundingMode.HALF_UP);
        BigDecimal normalizedStock = newStock.setScale(3, java.math.RoundingMode.HALF_UP);

        String currentHash = calculateTransactionHash(
                productId,
                normalizedDelta,
                normalizedStock,
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

        // Crear lote automáticamente para cualquier delta positivo sin targetBatchId
        // (incluye REVERSION para que se cree el lote consolidado)
        if (targetBatchId == null
                && quantityDelta.compareTo(BigDecimal.ZERO) > 0) {
            ProductBatch newBatch = productBatchService.createBatch(
                    product,
                    normalizedDelta,
                    expirationDate,
                    transaction);
            batchMovements.add(new BatchConsumptionDetail(newBatch.getId(), normalizedDelta.negate()));
        }

        // Guardar detalles de trazabilidad de lotes si existen
        if (!batchMovements.isEmpty()) {
            for (BatchConsumptionDetail detail : batchMovements) {
                ProductBatch affectedBatch = productBatchService.getBatchById(detail.getBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Lote no encontrado durante el registro de trazabilidad"));

                StockLedgerBatchDetail batchDetail = StockLedgerBatchDetail.builder()
                        .ledgerTransaction(transaction)
                        .batch(affectedBatch)
                        .quantity(detail.getQuantityConsumed().negate()) // Guardamos la cantidad tal cual afectó al
                                                                         // lote
                        .build();
                batchDetailRepository.save(batchDetail);
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

        log.info("Movimiento registrado: TX#{} Hash={}", nextSequence, currentHash.substring(0, 8));

        return transaction;
    }

    private String calculateTransactionHash(
            Integer productId,
            BigDecimal quantityDelta,
            BigDecimal resultingStock,
            LocalDateTime timestamp,
            String previousHash,
            Long sequenceNumber) {

        return ledgerHashTimer.record(() -> {
            try {
                String data = String.format("%d|%s|%s|%s|%s|%d",
                        productId,
                        quantityDelta.toPlainString(),
                        resultingStock.toPlainString(),
                        timestamp.toString(),
                        previousHash,
                        sequenceNumber);

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

                return HexFormat.of().formatHex(hashBytes);

            } catch (NoSuchAlgorithmException e) {
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

        List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

        if (chain.isEmpty()) {
            return new IntegrityCheckResult(productId, productName, true,
                    i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_NO_TRANSACTIONS),
                    null);
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

            // Normalizar los BigDecimal con la misma escala usada en la creación
            BigDecimal normalizedDelta = tx.getQuantityDelta().setScale(3, java.math.RoundingMode.HALF_UP);
            BigDecimal normalizedStock = tx.getResultingStock().setScale(3, java.math.RoundingMode.HALF_UP);

            LocalDateTime normalizedTimestamp = normalizeTimestamp(tx.getTransactionTimestamp());

            String recalculatedHash = calculateTransactionHash(
                    productId,
                    normalizedDelta,
                    normalizedStock,
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
            log.info("Cadena íntegra: {} transacciones verificadas", chain.size());
            return new IntegrityCheckResult(productId, productName, true,
                    i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_VALID, new Object[] { chain.size() }),
                    null);
        } else {
            log.debug("CORRUPCIÓN DETECTADA: {} errores encontrados", errors.size());
            return new IntegrityCheckResult(productId, productName, false,
                    i18nService.getMessage(MessageKey.LEDGER_INTEGRITY_CORRUPTED, new Object[] { errors.size() }),
                    errors);
        }
    }

    @Transactional
    public List<IntegrityCheckResult> verifyAllChains() {
        log.info("Verificando integridad de todas las cadenas...");

        List<StockSnapshot> snapshots = snapshotRepository.findAll();
        List<IntegrityCheckResult> results = new ArrayList<>();

        for (StockSnapshot snapshot : snapshots) {
            IntegrityCheckResult result = verifyChainIntegrity(snapshot.getProductId());
            results.add(result);

            snapshot.setIntegrityStatus(result.isValid() ? "VALID" : "CORRUPTED");
            snapshot.setLastVerified(LocalDateTime.now());
            snapshotRepository.save(snapshot);
        }

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

    @Transactional(rollbackFor = Exception.class)
    public String resetProductLedger(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InvalidOperationException("Producto no encontrado: " + productId));

        List<StockLedger> history = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);
        int deletedCount = history.size();

        log.warn("RESTABLECIENDO HISTORIAL: Producto {} - {} transacciones serán eliminadas",
                productId, deletedCount);

        batchDetailRepository.deleteAllByProductId(productId);
        batchRepository.deleteAllByProductId(productId);
        ledgerRepository.deleteAllByProductId(productId);
        snapshotRepository.deleteById(productId);

        log.info("Historial restablecido: Producto {} - {} transacciones eliminadas. Stock actual: {}",
                productId, deletedCount, product.getCurrentStock());

        return i18nService.getMessage(MessageKey.LEDGER_RESET_SUCCESS,
                new Object[] { deletedCount, product.getName(), product.getCurrentStock(), product.getUnit() });
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrityCheckResult repairProductLedger(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InvalidOperationException("Producto no encontrado: " + productId));

        List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

        if (chain.isEmpty()) {
            return new IntegrityCheckResult(productId, product.getName(), true,
                    i18nService.getMessage(MessageKey.LEDGER_REPAIR_NO_TRANSACTIONS), null);
        }

        String expectedPreviousHash = GENESIS_HASH;
        int repairedTransactions = 0;

        for (StockLedger tx : chain) {
            BigDecimal normalizedDelta = tx.getQuantityDelta().setScale(3, java.math.RoundingMode.HALF_UP);
            BigDecimal normalizedStock = tx.getResultingStock().setScale(3, java.math.RoundingMode.HALF_UP);
            LocalDateTime normalizedTimestamp = normalizeTimestamp(tx.getTransactionTimestamp());

            String recalculatedHash = calculateTransactionHash(
                    productId,
                    normalizedDelta,
                    normalizedStock,
                    normalizedTimestamp,
                    expectedPreviousHash,
                    tx.getSequenceNumber());

            boolean wasModified = !expectedPreviousHash.equals(tx.getPreviousHash())
                    || !recalculatedHash.equals(tx.getCurrentHash())
                    || !normalizedTimestamp.equals(tx.getTransactionTimestamp());

            tx.setPreviousHash(expectedPreviousHash);
            tx.setCurrentHash(recalculatedHash);
            tx.setTransactionTimestamp(normalizedTimestamp);
            tx.setVerified(true);

            if (wasModified) {
                repairedTransactions++;
            }

            expectedPreviousHash = recalculatedHash;
        }

        ledgerRepository.saveAll(chain);

        Optional<StockSnapshot> snapshotOptional = snapshotRepository.findById(productId);
        if (snapshotOptional.isPresent()) {
            StockSnapshot snapshot = snapshotOptional.get();
            snapshot.setLastTransactionHash(expectedPreviousHash);
            snapshot.setLastSequenceNumber(chain.get(chain.size() - 1).getSequenceNumber());
            snapshot.setLastVerified(LocalDateTime.now());
            snapshot.setIntegrityStatus("VALID");
            snapshotRepository.save(snapshot);
        }

        IntegrityCheckResult verification = verifyChainIntegrity(productId);
        String message = i18nService.getMessage(MessageKey.LEDGER_REPAIR_STATUS,
                new Object[] { repairedTransactions, chain.size(), verification.getMessage() });

        return new IntegrityCheckResult(
                productId,
                product.getName(),
                verification.isValid(),
                message,
                verification.getErrors());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> recordBatchStockMovements(
            List<BatchMovementItem> movements,
            User user,
            Integer orderId) {

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
                        null,
                        null);

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
                    .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + request.getOrderId()));
            
            order.setStatus(com.economato.inventory.domain.model.OrderStatus.CREATED);
            for (var detail : order.getDetails()) {
                detail.setQuantityReceived(null);
            }
            orderRepository.save(order);
            
            log.info("Reversión de orden completada: orderId={}, estado vuelto a CREATED", request.getOrderId());
            return List.of(); // En este caso no devolvemos los nuevos movimientos de la reversión para no confundir al DTO
        }

        if (request.getRecipeCookingAuditId() != null) {
            log.info("Procesando reversión automática de cocinado: recipeAuditId={}", request.getRecipeCookingAuditId());
            com.economato.inventory.domain.model.RecipeCookingAudit audit = recipeCookingAuditRepository.findById(request.getRecipeCookingAuditId())
                    .orElseThrow(() -> new ResourceNotFoundException("Auditoría de cocinado no encontrada: " + request.getRecipeCookingAuditId()));
            
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
     * @return Lista de IDs de productos con historial
     */
    @Transactional(readOnly = true)
    public List<Integer> getProductsWithLedger() {
        log.debug("Obteniendo productos con historial de ledger");
        return ledgerRepository.findAll().stream()
                .map(ledger -> ledger.getProduct().getId())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Verifica la integridad de las cadenas de todos los productos que tienen
     * ledger.
     * A diferencia de verifyAllChains(), este método solo verifica productos con
     * transacciones,
     * evitando procesar productos sin historial.
     * 
     * @return Lista de resultados de verificación de integridad
     */
    @Transactional(readOnly = true)
    public List<IntegrityCheckResult> verifyProductsWithLedger() {
        log.info("Verificando integridad de productos con ledger...");

        List<Integer> productIds = getProductsWithLedger();
        List<IntegrityCheckResult> results = new ArrayList<>();

        for (Integer productId : productIds) {
            IntegrityCheckResult result = verifyChainIntegrity(productId);
            results.add(result);
        }

        long validChains = results.stream().filter(IntegrityCheckResult::isValid).count();
        log.info("Verificación de productos con ledger completa: {}/{} cadenas íntegras",
                validChains, results.size());

        return results;
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
                    java.sql.Date sqlDate = (java.sql.Date) row[0];
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

    @Transactional(rollbackFor = Exception.class)
    public void withdrawExpiredBatch(Long batchId) {
        User user = securityContextHelper.getCurrentUser();
        ProductBatch batch = productBatchService.getBatchById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));

        if (batch.isDepleted() || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException("El lote ya no tiene stock para retirar");
        }

        BigDecimal quantity = batch.getRemainingQuantity();
        Integer productId = batch.getProduct().getId();

        // Retirada de lote caducado: es una salida, no necesita expirationDate (no crea lote nuevo)
        recordManualAdjustment(
                productId,
                quantity.negate(),
                MovementType.MERMA,
                "Retirada de lote caducado #" + batchId,
                user,
                batchId,
                null);

        log.info("Lote caducado retirado: batchId={}, productId={}, qty={}", batchId, productId, quantity);
    }
}
