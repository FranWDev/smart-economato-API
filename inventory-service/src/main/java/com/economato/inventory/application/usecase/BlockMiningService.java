package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BlockMiningService {

    private final LedgerProperties ledgerProperties;
    private final BlockchainProperties blockchainProperties;
    private final Timer miningTimer;
    private final Timer powTimer;
    private final Counter blocksMinedCounter;
    private final Counter nonceTotalCounter;
    private final AtomicLong nonceAccumulator = new AtomicLong(0);
    private final AtomicLong minedBlocksAccumulator = new AtomicLong(0);

    public BlockMiningService(
            LedgerProperties ledgerProperties,
            BlockchainProperties blockchainProperties,
            MeterRegistry meterRegistry) {
        this.ledgerProperties = ledgerProperties;
        this.blockchainProperties = blockchainProperties;
        this.miningTimer = Timer.builder("blockchain.mining.duration")
                .description("Block mining latency including PoW")
                .register(meterRegistry);
        this.powTimer = Timer.builder("blockchain.pow.duration")
                .description("Proof of work loop latency")
                .register(meterRegistry);
        this.blocksMinedCounter = Counter.builder("blockchain.blocks.mined.total")
                .description("Total mined blocks")
                .register(meterRegistry);
        this.nonceTotalCounter = Counter.builder("blockchain.pow.nonce.total")
                .description("Total nonce sum found by PoW")
                .register(meterRegistry);

        Gauge.builder("blockchain.pow.nonce.average", this,
                        service -> {
                            long mined = service.minedBlocksAccumulator.get();
                            if (mined == 0) {
                                return 0d;
                            }
                            return (double) service.nonceAccumulator.get() / mined;
                        })
                .description("Average nonce used to mine blocks")
                .register(meterRegistry);
    }

    public MiningResult mineBlock(Long blockNumber, String previousBlockHash, String merkleRoot, LocalDateTime timestamp) {
        return miningTimer.record(() -> {
            int difficulty = blockchainProperties.getDifficulty();
            String targetPrefix = "0".repeat(Math.max(0, difficulty));

            return powTimer.record(() -> {
                long nonce = 0L;
                while (true) {
                    String blockHash = computeBlockHash(blockNumber, previousBlockHash, merkleRoot, timestamp, nonce);
                    if (blockHash.startsWith(targetPrefix)) {
                        blocksMinedCounter.increment();
                        nonceTotalCounter.increment(nonce);
                        nonceAccumulator.addAndGet(nonce);
                        minedBlocksAccumulator.incrementAndGet();
                        return new MiningResult(blockHash, nonce, difficulty);
                    }
                    nonce++;
                }
            });
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

    public record MiningResult(String blockHash, long nonce, int difficulty) {
    }
}
