package com.economato.inventory.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class BlockSealingService {

    private final LedgerProperties ledgerProperties;
    private final Timer sealingTimer;
    private final Counter blocksSealedCounter;
    private final I18nService i18nService;

    public BlockSealingService(
            LedgerProperties ledgerProperties,
            MeterRegistry meterRegistry,
            I18nService i18nService) {
        this.ledgerProperties = ledgerProperties;
        this.i18nService = i18nService;
        this.sealingTimer = Timer.builder("blockchain.sealing.duration")
                .description("Block sealing latency")
                .register(meterRegistry);
        this.blocksSealedCounter = Counter.builder("blockchain.blocks.sealed.total")
                .description("Total sealed blocks")
                .register(meterRegistry);
    }

    public SealingResult sealBlock(Long blockNumber, String previousBlockHash, String merkleRoot, LocalDateTime timestamp) {
        return sealingTimer.record(() -> {
            String blockHash = computeBlockHash(blockNumber, previousBlockHash, merkleRoot, timestamp);
            blocksSealedCounter.increment();
            return new SealingResult(blockHash);
        });
    }

    private String computeBlockHash(
            Long blockNumber,
            String previousBlockHash,
            String merkleRoot,
            LocalDateTime timestamp) {
        String header = blockNumber + "|" + previousBlockHash + "|" + merkleRoot + "|" + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String secret = ledgerProperties.getHmacSecretForVersion(ledgerProperties.getCurrentHmacVersion());
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(header.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(i18nService.getMessage(MessageKey.ERROR_BLOCK_HASH_CALCULATION_FAILED), e);
        }
    }

    public record SealingResult(String blockHash) {
    }
}
