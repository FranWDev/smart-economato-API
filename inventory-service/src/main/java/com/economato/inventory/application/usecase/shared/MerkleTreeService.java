package com.economato.inventory.application.usecase.shared;

import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MerkleTreeService {

    private final LedgerProperties ledgerProperties;
    private final Timer merkleRootTimer;
    private final I18nService i18nService;

    public MerkleTreeService(LedgerProperties ledgerProperties, MeterRegistry meterRegistry, I18nService i18nService) {
        this.ledgerProperties = ledgerProperties;
        this.i18nService = i18nService;
        this.merkleRootTimer = Timer.builder("blockchain.merkle.root.duration")
                .description("Merkle root computation latency")
                .register(meterRegistry);
    }

    public String computeMerkleRoot(List<String> txHashes) {
        return merkleRootTimer.record(() -> doComputeMerkleRoot(txHashes));
    }

    public List<String> computeProof(List<String> txHashes, int index) {
        if (txHashes == null || txHashes.isEmpty()) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_EMPTY_TX_LIST));
        }
        if (index < 0 || index >= txHashes.size()) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_INVALID_INDEX, new Object[] { index }));
        }

        List<String> proof = new ArrayList<>();
        List<String> level = new ArrayList<>(txHashes);
        int currentIndex = index;

        while (level.size() > 1) {
            if (level.size() % 2 != 0) {
                level.add(level.get(level.size() - 1));
            }

            int siblingIndex = currentIndex ^ 1;
            String siblingHash = level.get(siblingIndex);
            String sidePrefix = (currentIndex % 2 == 0) ? "R:" : "L:";
            proof.add(sidePrefix + siblingHash);

            List<String> nextLevel = new ArrayList<>(level.size() / 2);
            for (int i = 0; i < level.size(); i += 2) {
                nextLevel.add(hmacSha256(level.get(i) + level.get(i + 1)));
            }

            currentIndex = currentIndex / 2;
            level = nextLevel;
        }

        return proof;
    }

    public boolean verifyProof(String txHash, List<String> proof, String merkleRoot) {
        if (txHash == null || merkleRoot == null) {
            return false;
        }

        String current = txHash;
        if (proof != null) {
            for (String step : proof) {
                if (step == null || step.length() < 3) {
                    return false;
                }
                String side = step.substring(0, 2);
                String siblingHash = step.substring(2);
                if ("L:".equals(side)) {
                    current = hmacSha256(siblingHash + current);
                } else if ("R:".equals(side)) {
                    current = hmacSha256(current + siblingHash);
                } else {
                    return false;
                }
            }
        }

        return current.equals(merkleRoot);
    }

    private String doComputeMerkleRoot(List<String> txHashes) {
        if (txHashes == null || txHashes.isEmpty()) {
            return hmacSha256("EMPTY_BLOCK");
        }
        if (txHashes.size() == 1) {
            return txHashes.get(0);
        }

        List<String> level = new ArrayList<>(txHashes);
        while (level.size() > 1) {
            if (level.size() % 2 != 0) {
                level.add(level.get(level.size() - 1));
            }

            List<String> nextLevel = new ArrayList<>(level.size() / 2);
            for (int i = 0; i < level.size(); i += 2) {
                nextLevel.add(hmacSha256(level.get(i) + level.get(i + 1)));
            }
            level = nextLevel;
        }

        return level.get(0);
    }

    public List<String> generateProofPath(List<String> leaves, int leafIndex) {
        if (leaves == null || leaves.isEmpty()) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_EMPTY_LEAF_LIST));
        }
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_INVALID_LEAF_INDEX, new Object[] { leafIndex }));
        }

        return computeProof(leaves, leafIndex);
    }

    public String rebuildFromProofPath(String leafHash, List<String> proofPath) {
        if (leafHash == null || leafHash.isEmpty()) {
            throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_NULL_LEAF_HASH));
        }
        
        String current = leafHash;
        if (proofPath != null) {
            for (String step : proofPath) {
                if (step == null || step.length() < 3) {
                    throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_INVALID_PATH_FORMAT, new Object[] { step }));
                }
                String side = step.substring(0, 2);
                String siblingHash = step.substring(2);
                
                if ("L:".equals(side)) {
                    current = hmacSha256(siblingHash + current);
                } else if ("R:".equals(side)) {
                    current = hmacSha256(current + siblingHash);
                } else {
                    throw new IllegalArgumentException(i18nService.getMessage(MessageKey.ERROR_MERKLE_INVALID_PATH_SIDE, new Object[] { side }));
                }
            }
        }
        
        return current;
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String secret = ledgerProperties.getHmacSecretForVersion(ledgerProperties.getCurrentHmacVersion());
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(i18nService.getMessage(MessageKey.ERROR_HMAC_CALCULATION_FAILED), e);
        }
    }
}
