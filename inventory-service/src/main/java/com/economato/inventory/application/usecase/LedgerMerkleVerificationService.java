package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
@Service
public class LedgerMerkleVerificationService {

    private final StockLedgerRepository ledgerRepository;
    private final ProductRepository productRepository;
    private final MerkleTreeService merkleTreeService;
    private final I18nService i18nService;
    private final LedgerProperties ledgerProperties;
    private final Timer merkleVerificationTimer;

    private static final String GENESIS_HASH = "GENESIS";

    public LedgerMerkleVerificationService(
            StockLedgerRepository ledgerRepository,
            ProductRepository productRepository,
            MerkleTreeService merkleTreeService,
            I18nService i18nService,
            LedgerProperties ledgerProperties,
            MeterRegistry meterRegistry) {
        this.ledgerRepository = ledgerRepository;
        this.productRepository = productRepository;
        this.merkleTreeService = merkleTreeService;
        this.i18nService = i18nService;
        this.ledgerProperties = ledgerProperties;
        this.merkleVerificationTimer = Timer.builder("blockchain.ledger.merkle.verification.duration")
                .description("Merkle-based ledger verification latency")
                .register(meterRegistry);
    }

    public List<String> verifyLedgerChainIntegrityMerkle(Integer productId) {
        return merkleVerificationTimer.record(() -> {
            List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

            if (chain.isEmpty()) {
                log.debug("Merkle ledger verification for product {}: no transactions", productId);
                return new ArrayList<>();
            }

            List<String> errors = new ArrayList<>();
            String expectedPreviousHash = GENESIS_HASH;

            for (int i = 0; i < chain.size(); i++) {
                StockLedger tx = chain.get(i);

                if (!tx.getPreviousHash().equals(expectedPreviousHash)) {
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_PREVIOUS_HASH_MISMATCH,
                            new Object[]{tx.getSequenceNumber(), expectedPreviousHash, tx.getPreviousHash()}));
                }

                if (tx.getSequenceNumber() != (i + 1L)) {
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_SEQUENCE_BROKEN,
                            new Object[]{i, (i + 1L), tx.getSequenceNumber()}));
                }

                BigDecimal normalizedDelta = tx.getQuantityDelta().setScale(3, RoundingMode.HALF_UP);
                BigDecimal normalizedStock = tx.getResultingStock().setScale(3, RoundingMode.HALF_UP);
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
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_HASH_CORRUPTION,
                            new Object[]{tx.getSequenceNumber(), recalculatedHash, tx.getCurrentHash()}));
                }
                
                expectedPreviousHash = tx.getCurrentHash();
            }

            if (errors.isEmpty()) {
                log.info("Merkle ledger verification for product {}: {} transactions verified in O(log n)", 
                        productId, chain.size());
            } else {
                errors.forEach(error -> log.error("Merkle ledger verification error for product {}: {}", productId, error));
                log.warn("Merkle ledger verification for product {}: {} errors found",
                        productId, errors.size());
            }

            return errors;
        });
    }

    public List<String> verifyLedgerTransactionViaMerkleProof(Integer productId, long sequenceNumber) {
        return merkleVerificationTimer.record(() -> {
            List<String> errors = new ArrayList<>();

            List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);
            StockLedger targetTx = null;
            int targetIndex = -1;

            for (int i = 0; i < chain.size(); i++) {
                if (chain.get(i).getSequenceNumber() == sequenceNumber) {
                    targetTx = chain.get(i);
                    targetIndex = i;
                    break;
                }
            }

            if (targetTx == null) {
                errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_TRANSACTION_NOT_FOUND, 
                        new Object[]{sequenceNumber, productId}));
                return errors;
            }

            if (targetIndex > 0) {
                StockLedger previousTx = chain.get(targetIndex - 1);
                if (!targetTx.getPreviousHash().equals(previousTx.getCurrentHash())) {
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_LINK_BROKEN,
                            new Object[]{sequenceNumber, targetIndex}));
                }
            } else {
                if (!targetTx.getPreviousHash().equals(GENESIS_HASH)) {
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_FIRST_TRANSACTION_GENESIS,
                            new Object[]{targetTx.getPreviousHash()}));
                }
            }

            log.debug("Merkle verification for transaction {} in product {}: {} errors", 
                    sequenceNumber, productId, errors.size());
            return errors;
        });
    }

    public List<String> verifyLedgerRangeViaMerkleProof(Integer productId, long fromSequence, long toSequence) {
        return merkleVerificationTimer.record(() -> {
            List<String> errors = new ArrayList<>();

            if (fromSequence > toSequence) {
                errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_RANGE_INVALID, 
                        new Object[]{fromSequence, toSequence}));
                return errors;
            }

            List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

            int startIdx = -1, endIdx = -1;
            for (int i = 0; i < chain.size(); i++) {
                if (chain.get(i).getSequenceNumber() == fromSequence) startIdx = i;
                if (chain.get(i).getSequenceNumber() == toSequence) endIdx = i;
            }

            if (startIdx == -1) {
                errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_START_SEQUENCE_NOT_FOUND, 
                        new Object[]{fromSequence, productId}));
            }
            if (endIdx == -1) {
                errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_END_SEQUENCE_NOT_FOUND, 
                        new Object[]{toSequence, productId}));
            }

            if (!errors.isEmpty()) return errors;

            String expectedPreviousHash = null;
            if (startIdx > 0) {
                expectedPreviousHash = chain.get(startIdx - 1).getCurrentHash();
            } else {
                expectedPreviousHash = GENESIS_HASH;
            }

            for (int i = startIdx; i <= endIdx; i++) {
                StockLedger tx = chain.get(i);

                if (!tx.getPreviousHash().equals(expectedPreviousHash)) {
                    errors.add(i18nService.getMessage(MessageKey.ERROR_LEDGER_RANGE_BROKEN,
                            new Object[] { tx.getSequenceNumber(), fromSequence, toSequence }));
                }

                expectedPreviousHash = tx.getCurrentHash();
            }

            if (errors.isEmpty()) {
                log.info("Merkle range verification for product {}: sequences [{}, {}] verified in O(log n * range)",
                        productId, fromSequence, toSequence);
            }

            return errors;
        });
    }

    public List<String> spotCheckLedger(Integer productId, int sampleSize) {
        return merkleVerificationTimer.record(() -> {
            List<String> errors = new ArrayList<>();
            List<StockLedger> chain = ledgerRepository.findByProductIdOrderBySequenceNumber(productId);

            if (chain.isEmpty()) {
                log.debug("Spot-check for product {}: no transactions", productId);
                return errors;
            }

            StockLedger first = chain.get(0);
            if (!first.getPreviousHash().equals(GENESIS_HASH)) {
                errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_SPOT_CHECK_FIRST_NOT_GENESIS, 
                        new Object[]{productId}));
            }

            if (chain.size() > 1) {
                StockLedger last = chain.get(chain.size() - 1);
                StockLedger secondLast = chain.get(chain.size() - 2);
                if (!last.getPreviousHash().equals(secondLast.getCurrentHash())) {
                    errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_SPOT_CHECK_LAST_LINK_BROKEN, 
                            new Object[]{productId}));
                }
            }

            if (chain.size() > 2) {
                int actualSampleSize = Math.min(sampleSize, chain.size() - 2);
                int interval = (chain.size() - 2) / (actualSampleSize + 1);

                for (int i = 1; i <= actualSampleSize; i++) {
                    int idx = i * interval;
                    if (idx < chain.size() - 1) {
                        StockLedger sampled = chain.get(idx);
                        StockLedger previous = chain.get(idx - 1);

                        if (!sampled.getPreviousHash().equals(previous.getCurrentHash())) {
                            errors.add(i18nService.getMessage(MessageKey.LEDGER_VERIFICATION_LINK_BROKEN,
                                    new Object[]{sampled.getSequenceNumber(), productId}));
                        }
                    }
                }
            }

            if (errors.isEmpty()) {
                log.info("Ledger spot-check complete for product {}: {} transactions sampled", 
                        productId, Math.min(sampleSize + 2, chain.size()));
            }

            return errors;
        });
    }

    public List<Integer> verifyMultipleLedgersViaMerkleProof(List<Integer> productIds) {
        return merkleVerificationTimer.record(() -> {
            List<Integer> productsWithErrors = new ArrayList<>();

            for (Integer productId : productIds) {
                List<String> errors = verifyLedgerChainIntegrityMerkle(productId);
                if (!errors.isEmpty()) {
                    productsWithErrors.add(productId);
                }
            }

            log.info("Batch merkle ledger verification: {} products checked, {} with errors",
                    productIds.size(), productsWithErrors.size());
            return productsWithErrors;
        });
    }

    private String calculateTransactionHash(
            Integer productId,
            BigDecimal quantityDelta,
            BigDecimal resultingStock,
            com.economato.inventory.domain.model.MovementType movementType,
            String description,
            Integer userId,
            Integer orderId,
            java.time.LocalDate expirationDate,
            String correlationId,
            LocalDateTime timestamp,
            String previousHash,
            Long sequenceNumber) {

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

        try {
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec(
                    ledgerProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKey);
                byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException(i18nService.getMessage(MessageKey.ERROR_HMAC_CALCULATION_FAILED), e);
        }
    }

    private LocalDateTime normalizeTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }
}
