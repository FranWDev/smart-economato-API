package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class BlockSealingService {

    private final LedgerProperties ledgerProperties;
    private final Timer sealingTimer;
    private final Counter blocksSealedCounter;

    public BlockSealingService(
            LedgerProperties ledgerProperties,
            MeterRegistry meterRegistry) {
        this.ledgerProperties = ledgerProperties;
        this.sealingTimer = Timer.builder("blockchain.sealing.duration")
                .description("Block sealing latency")
                .register(meterRegistry);
        this.blocksSealedCounter = Counter.builder("blockchain.blocks.sealed.total")
                .description("Total sealed blocks")
                .register(meterRegistry);
    }

    public SealingResult sealBlock(Long blockNumber, String previousBlockHash, String merkleRoot, LocalDateTime timestamp) {
        return sealingTimer.record(() -> {
            long nonce = 0L;
            String blockHash = computeBlockHash(blockNumber, previousBlockHash, merkleRoot, timestamp, nonce);
            blocksSealedCounter.increment();
            return new SealingResult(blockHash, nonce, 0);
        });
    }

    private String computeBlockHash(
            Long blockNumber,
            String previousBlockHash,
            String merkleRoot,
            LocalDateTime timestamp,
            long nonce) {
        String header = blockNumber + "|" + previousBlockHash + "|" + merkleRoot + "|" + timestamp + "|" + nonce;
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
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute block hash", e);
        }
    }

    public record SealingResult(String blockHash, long nonce, int difficulty) {
    }
}
