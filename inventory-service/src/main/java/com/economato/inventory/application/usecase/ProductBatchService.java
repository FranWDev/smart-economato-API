package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBatchService {

    private final ProductBatchRepository batchRepository;
    private final I18nService i18nService;

    @Transactional(rollbackFor = Exception.class)
    public ProductBatch createBatch(Product product, BigDecimal quantity, LocalDate expirationDate, StockLedger ledgerTx) {
        BigDecimal normalizedQuantity = quantity.setScale(3, java.math.RoundingMode.HALF_UP);
        ProductBatch batch = ProductBatch.builder()
                .product(product)
                .expirationDate(expirationDate)
            .initialQuantity(normalizedQuantity)
            .remainingQuantity(normalizedQuantity)
                .receivedAt(LocalDateTime.now())
                .ledgerTransaction(ledgerTx)
                .depleted(false)
                .build();

        ProductBatch saved = batchRepository.save(batch);
        log.debug("Lote creado: batchId={}, productId={}, qty={}, expiration={}",
                saved.getId(), product.getId(), quantity, expirationDate);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<BatchConsumptionDetail> consumeStock(Integer productId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        List<ProductBatch> batches = batchRepository.findActiveByProductIdOrderByExpiration(productId);
        BigDecimal remaining = quantity;
        List<ProductBatch> affected = new ArrayList<>();
        List<BatchConsumptionDetail> consumptionDetails = new ArrayList<>();

        for (ProductBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED));
            }

            BigDecimal toConsume = remaining.min(batch.getRemainingQuantity());
            if (toConsume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal newRemaining = batch.getRemainingQuantity()
                    .subtract(toConsume)
                    .setScale(3, java.math.RoundingMode.HALF_UP);
            batch.setRemainingQuantity(newRemaining);
            if (batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
                batch.setDepleted(true);
            }
            affected.add(batch);
            consumptionDetails.add(new BatchConsumptionDetail(batch.getId(), toConsume));
            remaining = remaining.subtract(toConsume);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_BATCH_INSUFFICIENT_STOCK));
        }

        batchRepository.saveAll(affected);
        return consumptionDetails;
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getActiveBatches(Integer productId) {
        return batchRepository.findByProductIdAndDepletedFalseOrderByExpirationDateAsc(productId);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveBatches(Integer productId) {
        return !batchRepository.findByProductIdAndDepletedFalseOrderByExpirationDateAsc(productId).isEmpty();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalBatchStock(Integer productId) {
        return batchRepository.sumRemainingQuantityByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getExpiringBatches(int days) {
        return batchRepository.findExpiringBefore(LocalDate.now().plusDays(days));
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getExpiredBatches() {
        return batchRepository.findExpiredWithRemainingStock();
    }

    @Transactional(readOnly = true)
    public Optional<ProductBatch> getBatchById(Long batchId) {
        return batchRepository.findById(batchId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void depleteBatch(Long batchId) {
        batchRepository.findById(batchId).ifPresent(batch -> {
            batch.setRemainingQuantity(BigDecimal.ZERO);
            batch.setDepleted(true);
            batchRepository.save(batch);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(ProductBatch batch) {
        batchRepository.save(batch);
    }
}
