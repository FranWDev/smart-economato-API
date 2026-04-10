package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MerkleTreeServiceTest {

    private final LedgerProperties ledgerProperties = new LedgerProperties();
    private MeterRegistry meterRegistry;

    private MerkleTreeService service;
    private static final String TEST_SECRET = "test-secret-for-merkle-tree-verification-32chars";

    @BeforeEach
    void setup() {
        ledgerProperties.setCurrentHmacVersion(1);
        ledgerProperties.setHmacSecret(TEST_SECRET);
        meterRegistry = new SimpleMeterRegistry();
        service = new MerkleTreeService(ledgerProperties, meterRegistry);
    }

    @Test
    void computeMerkleRoot_singleHash_returnsOriginalHash() {
        List<String> hashes = List.of("hash1");
        String root = service.computeMerkleRoot(hashes);

        assertNotNull(root);
        assertEquals("hash1", root);
    }

    @Test
    void computeMerkleRoot_twoHashes_returnsCombinedHash() {
        List<String> hashes = List.of("hash1", "hash2");
        String root = service.computeMerkleRoot(hashes);

        assertNotNull(root);
        assertEquals(64, root.length());
        // Should be deterministic
        String root2 = service.computeMerkleRoot(hashes);
        assertEquals(root, root2);
    }

    @Test
    void computeMerkleRoot_oddNumberOfHashes_padsProperly() {
        // 3 hashes should pad the third level
        List<String> hashes = List.of("hash1", "hash2", "hash3");
        String root = service.computeMerkleRoot(hashes);

        assertNotNull(root);
        assertEquals(64, root.length());

        // Should be deterministic even with odd count
        String root2 = service.computeMerkleRoot(hashes);
        assertEquals(root, root2);
    }

    @Test
    void computeMerkleRoot_manyHashes_buildsFullTree() {
        // Test with 10 hashes
        List<String> hashes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            hashes.add("hash" + i);
        }
        String root = service.computeMerkleRoot(hashes);

        assertNotNull(root);
        assertEquals(64, root.length());
        
        // Verify determinism
        String root2 = service.computeMerkleRoot(hashes);
        assertEquals(root, root2);
    }

    @Test
    void computeProof_singleHash_returnsEmptyProof() {
        List<String> hashes = List.of("hash1");
        List<String> proof = service.computeProof(hashes, 0);

        assertNotNull(proof);
        assertEquals(0, proof.size());
    }

    @Test
    void computeProof_twoHashesFirstIndex_includesSiblingHash() {
        List<String> hashes = List.of("hash1", "hash2");
        List<String> proof = service.computeProof(hashes, 0);

        assertNotNull(proof);
        assertEquals(1, proof.size());
        assertTrue(proof.get(0).startsWith("R:") || proof.get(0).startsWith("L:"));
    }

    @Test
    void computeProof_twoHashesSecondIndex_includesSiblingHash() {
        List<String> hashes = List.of("hash1", "hash2");
        List<String> proof = service.computeProof(hashes, 1);

        assertNotNull(proof);
        assertEquals(1, proof.size());
        assertTrue(proof.get(0).startsWith("R:") || proof.get(0).startsWith("L:"));
    }

    @Test
    void computeProof_multiLevelTree_includesAllPathNodes() {
        // 8 hashes creates a full binary tree of depth 3
        List<String> hashes = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            hashes.add("hash" + i);
        }

        List<String> proof = service.computeProof(hashes, 0);
        assertNotNull(proof);
        assertEquals(3, proof.size()); // Path length in full tree of 8 nodes
    }

    @Test
    void computeProof_oddNumberedTree_handlesProofCorrectly() {
        List<String> hashes = List.of("hash1", "hash2", "hash3");
        
        List<String> proof0 = service.computeProof(hashes, 0);
        List<String> proof1 = service.computeProof(hashes, 1);
        List<String> proof2 = service.computeProof(hashes, 2);

        assertNotNull(proof0);
        assertNotNull(proof1);
        assertNotNull(proof2);
        // All should have at least 1 sibling
        assertTrue(proof0.size() >= 1);
        assertTrue(proof1.size() >= 1);
        assertTrue(proof2.size() >= 1);
    }

    @Test
    void verifyProof_validProof_returnsTrue() {
        List<String> hashes = List.of("hash1", "hash2", "hash3", "hash4");
        String merkleRoot = service.computeMerkleRoot(hashes);
        String txHash = hashes.get(0);
        List<String> proof = service.computeProof(hashes, 0);

        boolean valid = service.verifyProof(txHash, proof, merkleRoot);
        assertTrue(valid);
    }

    @Test
    void verifyProof_validProofAnyIndex_returnsTrue() {
        List<String> hashes = List.of("hash1", "hash2", "hash3", "hash4");
        String merkleRoot = service.computeMerkleRoot(hashes);

        for (int i = 0; i < hashes.size(); i++) {
            String txHash = hashes.get(i);
            List<String> proof = service.computeProof(hashes, i);
            boolean valid = service.verifyProof(txHash, proof, merkleRoot);
            assertTrue(valid, "Proof verification failed for index " + i);
        }
    }

    @Test
    void verifyProof_invalidHash_returnsFalse() {
        List<String> hashes = List.of("hash1", "hash2", "hash3", "hash4");
        String merkleRoot = service.computeMerkleRoot(hashes);
        String wrongTxHash = "wronghash";
        List<String> proof = service.computeProof(hashes, 0);

        boolean valid = service.verifyProof(wrongTxHash, proof, merkleRoot);
        assertFalse(valid);
    }

    @Test
    void verifyProof_invalidRoot_returnsFalse() {
        List<String> hashes = List.of("hash1", "hash2", "hash3", "hash4");
        service.computeMerkleRoot(hashes);
        String txHash = hashes.get(0);
        List<String> proof = service.computeProof(hashes, 0);
        
        String wrongRoot = "0000000000000000000000000000000000000000000000000000000000000000";
        boolean valid = service.verifyProof(txHash, proof, wrongRoot);
        assertFalse(valid);
    }

    @Test
    void verifyProof_tamperedProof_returnsFalse() {
        List<String> hashes = List.of("hash1", "hash2", "hash3", "hash4");
        String merkleRoot = service.computeMerkleRoot(hashes);
        String txHash = hashes.get(0);
        List<String> proof = service.computeProof(hashes, 0);

        // Tamper with proof by modifying the sibling hash
        if (!proof.isEmpty()) {
            String tampered = proof.get(0);
            if (tampered.startsWith("R:")) {
                tampered = "R:0000000000000000000000000000000000000000000000000000000000000000";
            } else {
                tampered = "L:0000000000000000000000000000000000000000000000000000000000000000";
            }
            List<String> tamperedProof = new ArrayList<>(proof);
            tamperedProof.set(0, tampered);
            
            boolean valid = service.verifyProof(txHash, tamperedProof, merkleRoot);
            assertFalse(valid);
        }
    }

    @Test
    void computeMerkleRoot_emptyList_returnsGenesisHash() {
        List<String> emptyHashes = new ArrayList<>();

        String root = service.computeMerkleRoot(emptyHashes);

        assertNotNull(root);
        assertEquals(64, root.length());
        assertEquals(root, service.computeMerkleRoot(emptyHashes));
    }

    @Test
    void verifyProof_singleHashProof_returnsTrue() {
        List<String> hashes = List.of("hash1");
        String merkleRoot = service.computeMerkleRoot(hashes);
        String txHash = hashes.get(0);
        List<String> proof = service.computeProof(hashes, 0);

        boolean valid = service.verifyProof(txHash, proof, merkleRoot);
        assertTrue(valid);
    }

    @Test
    void computeMerkleRoot_largeTree_performsReasonably() {
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            hashes.add("hash" + i);
        }

        long start = System.currentTimeMillis();
        String root = service.computeMerkleRoot(hashes);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(root);
        assertEquals(64, root.length());
        // Should complete reasonably fast (< 1 second)
        assertTrue(duration < 1000, "Merkle root computation took " + duration + "ms");
    }

    @Test
    void verifyProof_largeTreeProof_performsReasonably() {
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            hashes.add("hash" + i);
        }

        String merkleRoot = service.computeMerkleRoot(hashes);
        String txHash = hashes.get(500);
        List<String> proof = service.computeProof(hashes, 500);

        long start = System.currentTimeMillis();
        boolean valid = service.verifyProof(txHash, proof, merkleRoot);
        long duration = System.currentTimeMillis() - start;

        assertTrue(valid);
        assertTrue(duration < 100, "Proof verification took " + duration + "ms");
    }

    @Test
    void verifyProof_consistencyWithDifferentInputOrders_produceDifferentRoots() {
        List<String> hashes1 = List.of("hash1", "hash2");
        List<String> hashes2 = List.of("hash2", "hash1");

        String root1 = service.computeMerkleRoot(hashes1);
        String root2 = service.computeMerkleRoot(hashes2);

        // Different input orders should produce different roots (not commutative)
        assertNotEquals(root1, root2);
    }
}
