package com.economatom.inventory.service;

import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.model.MovementType;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.StockLedger;
import com.economatom.inventory.model.StockSnapshot;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.ProductRepository;
import com.economatom.inventory.repository.StockLedgerRepository;
import com.economatom.inventory.repository.StockSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class StockLedgerService {

    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final Environment environment;

    private static final String GENESIS_HASH = "GENESIS";

    public StockLedgerService(
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            ProductRepository productRepository,
            Environment environment) {
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.environment = environment;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public StockLedger recordStockMovement(
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

        LocalDateTime now = LocalDateTime.now();

        String currentHash = calculateTransactionHash(
                productId,
                quantityDelta,
                newStock,
                now,
                previousHash,
                nextSequence);

        StockLedger transaction = StockLedger.builder()
                .product(product)
                .quantityDelta(quantityDelta)
                .resultingStock(newStock)
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

        snapshot.setCurrentStock(newStock);
        snapshot.setLastTransactionHash(currentHash);
        snapshot.setLastSequenceNumber(nextSequence);
        snapshot.setLastUpdated(now);
        snapshot.setIntegrityStatus("VALID");
        snapshotRepository.save(snapshot);

        product.setCurrentStock(newStock);
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

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error calculando hash SHA-256", e);
        }
    }

    @Transactional(readOnly = true)
    public IntegrityCheckResult verifyChainIntegrity(Integer productId) {
        log.info("Verificando integridad del ledger para producto {}", productId);

        List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

        if (chain.isEmpty()) {
            return new IntegrityCheckResult(true, "No hay transacciones para este producto", null);
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
                        tx.getPreviousHash().substring(0, 8));
                errors.add(error);
            }

            String recalculatedHash = calculateTransactionHash(
                    productId,
                    tx.getQuantityDelta(),
                    tx.getResultingStock(),
                    tx.getTransactionTimestamp(),
                    tx.getPreviousHash(),
                    tx.getSequenceNumber());

            if (!recalculatedHash.equals(tx.getCurrentHash())) {
                String error = String.format(
                        "TX#%d: Hash corrupto. Esperado: %s, Encontrado: %s. " +
                                "Datos: delta=%s, stock=%s",
                        tx.getSequenceNumber(),
                        recalculatedHash.substring(0, 8),
                        tx.getCurrentHash().substring(0, 8),
                        tx.getQuantityDelta(),
                        tx.getResultingStock());
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
            return new IntegrityCheckResult(true,
                    String.format("Cadena íntegra: %d transacciones verificadas", chain.size()),
                    null);
        } else {
            log.error("CORRUPCIÓN DETECTADA: {} errores encontrados", errors.size());
            return new IntegrityCheckResult(false,
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
    public Optional<StockSnapshot> getCurrentStock(Integer productId) {
        return snapshotRepository.findById(productId);
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

    public static class IntegrityCheckResult {
        private final boolean valid;
        private final String message;
        private final List<String> errors;

        public IntegrityCheckResult(boolean valid, String message, List<String> errors) {
            this.valid = valid;
            this.message = message;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
