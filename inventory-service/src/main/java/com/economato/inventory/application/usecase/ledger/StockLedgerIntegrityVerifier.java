package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StockLedgerIntegrityVerifier {

    private final I18nService i18nService;
    private final ProductRepository productRepository;
    private final StockLedgerRepository ledgerRepository;
    private final LedgerChainVerificationService ledgerChainVerificationService;
    private final StockMovementRecorder stockMovementRecorder;
    private final StockSnapshotRepository snapshotRepository;
    private final ProductBatchRepository batchRepository;

    private static final String GENESIS_HASH = "GENESIS";

    public StockLedgerIntegrityVerifier(
            I18nService i18nService,
            ProductRepository productRepository,
            StockLedgerRepository ledgerRepository,
            LedgerChainVerificationService ledgerChainVerificationService,
            StockMovementRecorder stockMovementRecorder,
            StockSnapshotRepository snapshotRepository,
            ProductBatchRepository batchRepository) {
        this.i18nService = i18nService;
        this.productRepository = productRepository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerChainVerificationService = ledgerChainVerificationService;
        this.stockMovementRecorder = stockMovementRecorder;
        this.snapshotRepository = snapshotRepository;
        this.batchRepository = batchRepository;
    }

    public IntegrityCheckResult verifyChainIntegrity(Integer productId) {
        log.info("Verificando integridad del ledger para producto {}", productId);

        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("Desconocido");

        List<String> merkleErrors = ledgerChainVerificationService.verifyLedgerChainIntegrity(productId);
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

                String recalculatedHash = stockMovementRecorder.calculateTransactionHash(
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

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "weekly_plan", "weekly_plan_requirements", "student_metrics" }, allEntries = true)
    public void synchronizeStockWithLedger() {
        log.info("Iniciando sincronización exhaustiva de stock con el ledger...");
        List<Product> products = productRepository.findAll();
        List<Product> productsToSave = new ArrayList<>();
        List<StockSnapshot> snapshotsToSave = new ArrayList<>();
        List<ProductBatch> batchesToSave = new ArrayList<>();

        for (Product product : products) {
            Optional<StockLedger> lastTx = ledgerRepository.findLastTransactionByProductId(product.getId());
            BigDecimal realStock = lastTx.map(StockLedger::getResultingStock).orElse(BigDecimal.ZERO);
            String lastHash = lastTx.map(StockLedger::getCurrentHash).orElse(GENESIS_HASH);
            Long lastSeq = lastTx.map(StockLedger::getSequenceNumber).orElse(0L);

            if (product.getCurrentStock().compareTo(realStock) != 0) {
                log.info("Corrigiendo stock de producto {}: {} -> {}", product.getId(), product.getCurrentStock(), realStock);
                product.setCurrentStock(realStock);
                productsToSave.add(product);
            }

            StockSnapshot snapshot = snapshotRepository.findById(product.getId())
                    .orElseGet(() -> stockMovementRecorder.createInitialSnapshot(product));
            
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

                String newHash = stockMovementRecorder.calculateTransactionHash(
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

                tx.setResultingStock(newResultingStock);
                tx.setPreviousHash(previousHash);
                tx.setCurrentHash(newHash);
                tx.setVerified(true);

                currentStock = newResultingStock;
                previousHash = newHash;
            }

            ledgerRepository.saveAll(transactions);
            ledgerRepository.flush();

            product.setCurrentStock(currentStock);
            productsToUpdate.add(product);

            StockSnapshot snapshot = snapshotRepository.findById(product.getId())
                    .orElseGet(() -> stockMovementRecorder.createInitialSnapshot(product));
            
            snapshot.setCurrentStock(currentStock);
            snapshot.setLastTransactionHash(previousHash);
            snapshot.setLastSequenceNumber((long) transactions.size());
            snapshot.setLastUpdated(LocalDateTime.now());
            snapshot.setIntegrityStatus("VALID");
            snapshotsToUpdate.add(snapshot);
            
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

    private LocalDateTime normalizeTimestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp.truncatedTo(ChronoUnit.MILLIS) : null;
    }
}
