package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BlockMiningServiceTest {

    private final LedgerProperties ledgerProperties = new LedgerProperties();
    private final BlockchainProperties blockchainProperties = new BlockchainProperties();
    private MeterRegistry meterRegistry;

    private BlockMiningService service;
    private static final String TEST_SECRET = "test-secret-for-block-mining-verification-32chars";

    @BeforeEach
    void setup() {
        ledgerProperties.setCurrentHmacVersion(1);
        ledgerProperties.setHmacSecret(TEST_SECRET);
        blockchainProperties.setDifficulty(0);
        meterRegistry = new SimpleMeterRegistry();
        service = new BlockMiningService(ledgerProperties, blockchainProperties, meterRegistry);
    }

    @Test
    void mineBlock_withDifficultyZero_returnsSameHash() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        assertNotNull(result);
        assertNotNull(result.blockHash());
        assertEquals(64, result.blockHash().length());
        assertEquals(0L, result.nonce());
        assertEquals(0, result.difficulty());
    }

    @Test
    void mineBlock_withDifficultyOne_hasCorrectPrefix() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        // Mock difficulty property to return 1
        blockchainProperties.setDifficulty(1);

        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        assertNotNull(result);
        assertTrue(result.blockHash().startsWith("0"), 
            "Block hash should start with '0' for difficulty 1");
        assertEquals(64, result.blockHash().length());
        assertTrue(result.nonce() >= 0);
        assertEquals(1, result.difficulty());
    }

    @Test
    void mineBlock_withDifficultyTwo_hasCorrectPrefix() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        // Mock difficulty property to return 2
        blockchainProperties.setDifficulty(2);

        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        assertNotNull(result);
        assertTrue(result.blockHash().startsWith("00"), 
            "Block hash should start with '00' for difficulty 2");
        assertEquals(64, result.blockHash().length());
        assertTrue(result.nonce() >= 0);
        assertEquals(2, result.difficulty());
    }

    @Test
    void mineBlock_deterministicResultsForSameInputs() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 12, 0, 0);

        blockchainProperties.setDifficulty(1);

        BlockMiningService.MiningResult result1 = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );
        BlockMiningService.MiningResult result2 = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        assertEquals(result1.blockHash(), result2.blockHash());
        assertEquals(result1.nonce(), result2.nonce());
        assertEquals(result1.difficulty(), result2.difficulty());
    }

    @Test
    void mineBlock_differentBlockNumbers_produceDifferentHashes() {
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 12, 0, 0);

        blockchainProperties.setDifficulty(0);

        BlockMiningService.MiningResult result1 = service.mineBlock(
            1L, previousBlockHash, merkleRoot, timestamp
        );
        BlockMiningService.MiningResult result2 = service.mineBlock(
            2L, previousBlockHash, merkleRoot, timestamp
        );

        assertNotEquals(result1.blockHash(), result2.blockHash());
    }

    @Test
    void mineBlock_differentMerkleRoots_produceDifferentHashes() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 12, 0, 0);

        blockchainProperties.setDifficulty(0);

        BlockMiningService.MiningResult result1 = service.mineBlock(
            blockNumber, previousBlockHash, "1111111111111111111111111111111111111111111111111111111111111111", timestamp
        );
        BlockMiningService.MiningResult result2 = service.mineBlock(
            blockNumber, previousBlockHash, "2222222222222222222222222222222222222222222222222222222222222222", timestamp
        );

        assertNotEquals(result1.blockHash(), result2.blockHash());
    }

    @Test
    void mineBlock_differentPreviousBlockHash_produceDifferentHashes() {
        long blockNumber = 1L;
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 12, 0, 0);

        blockchainProperties.setDifficulty(0);

        BlockMiningService.MiningResult result1 = service.mineBlock(
            blockNumber, "0000000000000000000000000000000000000000000000000000000000000000", merkleRoot, timestamp
        );
        BlockMiningService.MiningResult result2 = service.mineBlock(
            blockNumber, "1111111111111111111111111111111111111111111111111111111111111111", merkleRoot, timestamp
        );

        assertNotEquals(result1.blockHash(), result2.blockHash());
    }

    @Test
    void mineBlock_blockHashIsValidHex() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        blockchainProperties.setDifficulty(0);

        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        // Should be valid hex string
        assertTrue(isValidHex(result.blockHash()), "Block hash should be valid hex");
    }

    @Test
    void mineBlock_nonceIncrementsUntilSuccess() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        blockchainProperties.setDifficulty(2);

        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );

        // With difficulty 2, should have found valid hash with some nonce
        assertNotNull(result);
        assertTrue(result.blockHash().startsWith("00"));
        assertTrue(result.nonce() >= 0);
    }

    @Test
    void mineBlock_performanceWithLowDifficulty_completesFast() {
        long blockNumber = 1L;
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        blockchainProperties.setDifficulty(1);

        long start = System.currentTimeMillis();
        BlockMiningService.MiningResult result = service.mineBlock(
            blockNumber, previousBlockHash, merkleRoot, timestamp
        );
        long duration = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(duration < 5000, "Mining with difficulty 1 should complete reasonably fast");
    }

    @Test
    void mineBlock_resultHashesAreConsistentLength() {
        blockchainProperties.setDifficulty(1);

        for (long blockNumber = 1; blockNumber <= 5; blockNumber++) {
            BlockMiningService.MiningResult result = service.mineBlock(
                blockNumber, 
                "0000000000000000000000000000000000000000000000000000000000000000",
                "1111111111111111111111111111111111111111111111111111111111111111",
                LocalDateTime.now()
            );

            assertEquals(64, result.blockHash().length(), 
                "Block hash length should always be 64 chars");
        }
    }

    @Test
    void mineBlock_difficultyThree_hasCorrectPrefix() {
        blockchainProperties.setDifficulty(3);

        BlockMiningService.MiningResult result = service.mineBlock(
            1L,
            "0000000000000000000000000000000000000000000000000000000000000000",
            "1111111111111111111111111111111111111111111111111111111111111111",
            LocalDateTime.now()
        );

        assertNotNull(result);
        assertTrue(result.blockHash().startsWith("000"), 
            "Block hash should start with '000' for difficulty 3");
    }

    @Test
    void mineBlock_highDifficultyProducesStricterPrefix() {
        String previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String merkleRoot = "1111111111111111111111111111111111111111111111111111111111111111";
        LocalDateTime timestamp = LocalDateTime.now();

        blockchainProperties.setDifficulty(1);
        BlockMiningService.MiningResult result1 = service.mineBlock(1L, previousBlockHash, merkleRoot, timestamp);

        blockchainProperties.setDifficulty(2);
        BlockMiningService.MiningResult result2 = service.mineBlock(2L, previousBlockHash, merkleRoot, timestamp);

        assertEquals(1, result1.difficulty());
        assertEquals(2, result2.difficulty());
        assertTrue(result1.blockHash().startsWith("0"));
        assertTrue(result2.blockHash().startsWith("00"));
    }

    private boolean isValidHex(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.matches("^[0-9a-f]+$");
    }
}
