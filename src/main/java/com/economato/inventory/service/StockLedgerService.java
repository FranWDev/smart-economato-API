package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.BatchStockMovementRequestDTO;
import com.economato.inventory.dto.request.BatchMovementItem;
import com.economato.inventory.dto.response.IntegrityCheckResult;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.StockSnapshot;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.StockSnapshotRepository;
import com.economato.inventory.security.SecurityContextHelper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    private final SecurityContextHelper securityContextHelper;
    private final Environment environment;

    // Métricas declaradas como final para thread-safety
    private final Counter stockMovementsCounter;
    private final Timer ledgerHashTimer;

    private static final String GENESIS_HASH = "GENESIS";

    public StockLedgerService(
            I18nService i18nService,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            SecurityContextHelper securityContextHelper,
            Environment environment,
            MeterRegistry meterRegistry) {
        this.i18nService = i18nService;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.securityContextHelper = securityContextHelper;
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

        return recordStockMovementInternal(productId, quantityDelta, movementType, description, user, orderId);
    }

    private StockLedger recordStockMovementInternal(
            Integer productId,
            BigDecimal quantityDelta,
            MovementType movementType,
            String description,
            User user,
            Integer orderId) {

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
                    String.format("Stock insuficiente. Actual: %s, Solicitado: %s",
                            snapshot.getCurrentStock(), quantityDelta.abs()));
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
                .sequenceNumber(nextSequence)
                .verified(true)
                .build();

        transaction = ledgerRepository.save(transaction);

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

                return java.util.HexFormat.of().formatHex(hashBytes);

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
            return new IntegrityCheckResult(productId, productName, true, "No hay transacciones para este producto",
                    null);
        }

        List<String> errors = new ArrayList<>();
        String expectedPreviousHash = GENESIS_HASH;

        for (int i = 0; i < chain.size(); i++) {
            StockLedger tx = chain.get(i);

            if (!tx.getPreviousHash().equals(expectedPreviousHash)) {
                String error = String.format(
                        "TX#%d: previousHash incorrecto. Esperado: %s, Encontrado: %s",
                        tx.getSequenceNumber(),
                        expectedPreviousHash.substring(0, Math.min(8, expectedPreviousHash.length())),
                        tx.getPreviousHash().substring(0, Math.min(8, tx.getPreviousHash().length())));
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
                String error = String.format(
                        "TX#%d: Hash corrupto. Esperado: %s, Encontrado: %s. " +
                                "Datos: delta=%s, stock=%s",
                        tx.getSequenceNumber(),
                        recalculatedHash.substring(0, Math.min(8, recalculatedHash.length())),
                        tx.getCurrentHash().substring(0, Math.min(8, tx.getCurrentHash().length())),
                        normalizedDelta,
                        normalizedStock);
                errors.add(error);
            }

            if (tx.getSequenceNumber() != (i + 1L)) {
                String error = String.format(
                        "TX#%d: Secuencia rota. Esperado: %d",
                        tx.getSequenceNumber(), (i + 1L));
                errors.add(error);
            }

            expectedPreviousHash = tx.getCurrentHash();
        }

        if (errors.isEmpty()) {
            log.info("Cadena íntegra: {} transacciones verificadas", chain.size());
            return new IntegrityCheckResult(productId, productName, true,
                    String.format("Cadena íntegra: %d transacciones verificadas", chain.size()),
                    null);
        } else {
            log.error("CORRUPCIÓN DETECTADA: {} errores encontrados", errors.size());
            return new IntegrityCheckResult(productId, productName, false,
                    String.format("CORRUPCIÓN DETECTADA: %d errores", errors.size()),
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
                return ledgerRepository.findByProductIdOrderBySequenceNumber(productId, pageable);
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

        ledgerRepository.deleteAllByProductId(productId);
        snapshotRepository.deleteById(productId);

        log.info("Historial restablecido: Producto {} - {} transacciones eliminadas. Stock actual: {}",
                productId, deletedCount, product.getCurrentStock());

        return String.format("Historial restablecido correctamente. %d transacciones eliminadas. " +
                "El producto %s vuelve a empezar con stock limpio: %s %s",
                deletedCount, product.getName(), product.getCurrentStock(), product.getUnit());
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrityCheckResult repairProductLedger(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InvalidOperationException("Producto no encontrado: " + productId));

        List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

        if (chain.isEmpty()) {
            return new IntegrityCheckResult(productId, product.getName(), true,
                    "No hay transacciones para reparar en este producto", null);
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
        String message = String.format(
                "Ledger reparado: %d/%d transacciones actualizadas. %s",
                repairedTransactions,
                chain.size(),
                verification.getMessage());

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
                        orderId);
                transactions.add(transaction);
            }

            log.info("Operación batch completada exitosamente: {} transacciones registradas",
                    transactions.size());

            return transactions;

        } catch (Exception e) {
            log.error("Error en operación batch. Revertiendo {} transacciones", transactions.size(), e);
            throw new InvalidOperationException(
                    "Error en operación batch: " + e.getMessage() +
                            ". Se han revertido todos los cambios.");
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<StockLedger> processBatchMovements(BatchStockMovementRequestDTO request) {
        User currentUser = securityContextHelper.getCurrentUser();

        List<BatchMovementItem> movements = request.getMovements().stream()
                .map(item -> new BatchMovementItem(
                        item.getProductId(),
                        item.getQuantityDelta(),
                        item.getMovementType(),
                        item.getDescription() != null ? item.getDescription() : request.getReason()))
                .collect(java.util.stream.Collectors.toList());

        List<StockLedger> transactions = recordBatchStockMovements(movements, currentUser, request.getOrderId());

        if (request.getOrderId() != null) {
            orderRepository.findById(request.getOrderId()).ifPresent(orderRepository::delete);
            log.info("Operación batch: Orden eliminada ID={}", request.getOrderId());
        }

        if (request.getRecipeCookingAuditId() != null) {
            recipeCookingAuditRepository.findById(request.getRecipeCookingAuditId())
                    .ifPresent(recipeCookingAuditRepository::delete);
            log.info("Operación batch: Auditoría de receta cocinada eliminada ID={}",
                    request.getRecipeCookingAuditId());
        }

        return transactions;
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
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Verifica la integridad de las cadenas de todos los productos que tienen ledger.
     * A diferencia de verifyAllChains(), este método solo verifica productos con transacciones,
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

}
