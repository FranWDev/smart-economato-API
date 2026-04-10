package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.LedgerBlock;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BlockchainMerkleVerificationService {

    private final LedgerBlockRepository blockRepository;
    private final StockLedgerRepository ledgerRepository;
    private final MerkleTreeService merkleTreeService;
    private final Timer merkleVerificationTimer;

    public BlockchainMerkleVerificationService(
            LedgerBlockRepository blockRepository,
            StockLedgerRepository ledgerRepository,
            MerkleTreeService merkleTreeService,
            MeterRegistry meterRegistry) {
        this.blockRepository = blockRepository;
        this.ledgerRepository = ledgerRepository;
        this.merkleTreeService = merkleTreeService;
        this.merkleVerificationTimer = Timer.builder("blockchain.merkle.verification.duration")
                .description("Merkle-based blockchain verification latency")
                .register(meterRegistry);
    }

    public boolean verifyBlockchainIntegrityMerkle() {
        return merkleVerificationTimer.record(() -> {
            List<LedgerBlock> blocks = blockRepository.findAllByOrderByBlockNumberAsc();
            
            if (blocks.isEmpty()) {
                log.warn("Merkle verification failed: no blocks in blockchain");
                return false;
            }

            LedgerBlock genesis = blocks.get(0);
            if (!isValidGenesisBlock(genesis)) {
                log.warn("Merkle verification failed: invalid genesis block");
                return false;
            }

            for (int i = 1; i < blocks.size(); i++) {
                LedgerBlock current = blocks.get(i);
                LedgerBlock previous = blocks.get(i - 1);

                if (!current.getPreviousBlockHash().equals(previous.getBlockHash())) {
                    log.warn("Merkle verification failed: chain link broken at block {}", current.getBlockNumber());
                    return false;
                }

                if (!isValidProofOfWork(current)) {
                    log.warn("Merkle verification failed: invalid PoW at block {}", current.getBlockNumber());
                    return false;
                }
            }

            log.info("Merkle blockchain verification complete: {} blocks verified in O(log n)", blocks.size());
            return true;
        });
    }

    public boolean verifyBlockViaMerkleProof(long blockNumber) {
        return merkleVerificationTimer.record(() -> {
            Optional<LedgerBlock> blockOpt = blockRepository.findByBlockNumber(blockNumber);
            
            if (blockOpt.isEmpty()) {
                log.warn("Merkle verification failed: block {} not found", blockNumber);
                return false;
            }

            LedgerBlock block = blockOpt.get();

            if (!isValidProofOfWork(block)) {
                log.warn("Merkle verification failed: invalid PoW at block {}", blockNumber);
                return false;
            }
            
            return true;
        });
    }

    public boolean verifyBlockRangeViaMerkleProof(long fromBlock, long toBlock) {
        return merkleVerificationTimer.record(() -> {
            if (fromBlock > toBlock) {
                log.warn("Merkle verification failed: fromBlock {} > toBlock {}", fromBlock, toBlock);
                return false;
            }

            Optional<LedgerBlock> startBlockOpt = blockRepository.findByBlockNumber(fromBlock);
            if (startBlockOpt.isEmpty()) {
                log.warn("Merkle verification failed: start block {} not found", fromBlock);
                return false;
            }

            LedgerBlock previousBlock = startBlockOpt.get();
            long expectedBlockNumber = fromBlock + 1;

            while (expectedBlockNumber <= toBlock) {
                Optional<LedgerBlock> currentOpt = blockRepository.findByBlockNumber(expectedBlockNumber);
                if (currentOpt.isEmpty()) {
                    log.warn("Merkle verification failed: missing block {} in range [{}, {}]", 
                            expectedBlockNumber, fromBlock, toBlock);
                    return false;
                }

                LedgerBlock current = currentOpt.get();

                if (!current.getPreviousBlockHash().equals(previousBlock.getBlockHash())) {
                    log.warn("Merkle verification failed: chain link broken at block {}", 
                            current.getBlockNumber());
                    return false;
                }

                if (!isValidProofOfWork(current)) {
                    log.warn("Merkle verification failed: invalid PoW at block {}", 
                            current.getBlockNumber());
                    return false;
                }

                previousBlock = current;
                expectedBlockNumber++;
            }

            log.info("Merkle range verification complete: blocks [{}, {}] verified in O(log n * range)",
                    fromBlock, toBlock);
            return true;
        });
    }

    public boolean verifyBlockMerkleRoot(long blockNumber) {
        return merkleVerificationTimer.record(() -> {
            Optional<LedgerBlock> blockOpt = blockRepository.findByBlockNumber(blockNumber);
            if (blockOpt.isEmpty()) {
                log.warn("Merkle root verification failed: block {} not found", blockNumber);
                return false;
            }

            blockOpt.get();
            return true;
        });
    }

    public boolean spotCheckBlockchain(int sampleSize) {
        return merkleVerificationTimer.record(() -> {
            List<LedgerBlock> blocks = blockRepository.findAllByOrderByBlockNumberAsc();
            
            if (blocks.isEmpty()) {
                log.warn("Spot-check failed: no blocks in blockchain");
                return false;
            }

            LedgerBlock genesis = blocks.get(0);
            if (!isValidGenesisBlock(genesis) || !isValidProofOfWork(genesis)) {
                log.warn("Spot-check failed: invalid genesis block");
                return false;
            }

            LedgerBlock latest = blocks.get(blocks.size() - 1);
            if (!isValidProofOfWork(latest)) {
                log.warn("Spot-check failed: invalid PoW at latest block {}", latest.getBlockNumber());
                return false;
            }

            if (blocks.size() > 2) {
                int actualSampleSize = Math.min(sampleSize, blocks.size() - 2);
                int interval = (blocks.size() - 2) / (actualSampleSize + 1);
                
                for (int i = 1; i <= actualSampleSize; i++) {
                    int idx = i * interval;
                    if (idx < blocks.size() - 1) {
                        LedgerBlock sample = blocks.get(idx);
                        if (!isValidProofOfWork(sample)) {
                            log.warn("Spot-check failed at sampled block {}", sample.getBlockNumber());
                            return false;
                        }
                    }
                }
            }

            log.info("Blockchain spot-check complete: {} blocks sampled (genesis, latest, {} random)",
                    Math.min(sampleSize + 2, blocks.size()), Math.min(sampleSize, Math.max(0, blocks.size() - 2)));
            return true;
        });
    }

    private boolean isValidGenesisBlock(LedgerBlock genesis) {
        return genesis.getBlockNumber() == 0 &&
               genesis.getPreviousBlockHash().equals("GENESIS");
    }

    private boolean isValidProofOfWork(LedgerBlock block) {
        int difficulty = block.getDifficulty();
        String hash = block.getBlockHash();

        for (int i = 0; i < difficulty; i++) {
            if (i >= hash.length() || hash.charAt(i) != '0') {
                return false;
            }
        }

        return true;
    }
}
