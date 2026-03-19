package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**-
 * Servicio para gestionar lotes de productos, incluyendo creación, consumo, actualización de caducidad y consultas.
 * Encapsula la lógica de negocio relacionada con el manejo de lotes, como validaciones de fechas, cálculos de stock
 * restante y manejo de estados (agotado/no agotado).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBatchService {

    private final ProductBatchRepository batchRepository;
    private final I18nService i18nService;

    @Transactional(rollbackFor = Exception.class)
    public ProductBatch createBatch(Product product, BigDecimal quantity, LocalDate expirationDate, StockLedger ledgerTx) {
        if (expirationDate == null) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
        }
        if (expirationDate.isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST, new Object[]{expirationDate}));
        }
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

    @Transactional(rollbackFor = Exception.class)
    public List<BatchConsumptionDetail> consumeFromSpecificBatch(Long batchId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));

        if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT,
                        new Object[]{batchId, batch.getProduct().getName(), batch.getExpirationDate()}));
        }

        if (batch.isDepleted() || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException("El lote específico está agotado.");
        }

        BigDecimal toConsume = quantity.min(batch.getRemainingQuantity());
        BigDecimal newRemaining = batch.getRemainingQuantity()
                .subtract(toConsume)
                .setScale(3, java.math.RoundingMode.HALF_UP);

        batch.setRemainingQuantity(newRemaining);
        if (batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            batch.setDepleted(true);
        }
        batchRepository.save(batch);

        if (quantity.compareTo(toConsume) > 0) {
            throw new InvalidOperationException("El lote específico no tiene suficiente stock para cubrir todo el ajuste.");
        }

        return List.of(new BatchConsumptionDetail(batch.getId(), toConsume));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addStockToBatch(Long batchId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));

        if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_ADD_STOCK, new Object[]{batchId}));
        }

        BigDecimal newRemaining = batch.getRemainingQuantity()
                .add(quantity)
                .setScale(3, java.math.RoundingMode.HALF_UP);

        log.info("Añadiendo stock al lote {}: anterior={}, añadir={}, nuevo={}, antes_depleted={}", 
                batchId, batch.getRemainingQuantity(), quantity, newRemaining, batch.isDepleted());

        batch.setRemainingQuantity(newRemaining);
        if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
            batch.setDepleted(false);
            log.info("Lote {} marcado como NO agotado", batchId);
        }
        batchRepository.saveAndFlush(batch);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductBatch updateExpirationDate(Long batchId, LocalDate newExpirationDate, String reason) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado: " + batchId));

        if (batch.isDepleted()) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_DEPLETED_CANNOT_UPDATE));
        }

        if (newExpirationDate == null) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
        }

        if (newExpirationDate.isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST, new Object[]{newExpirationDate}));
        }

        LocalDate previousDate = batch.getExpirationDate();
        batch.setExpirationDate(newExpirationDate);
        ProductBatch saved = batchRepository.save(batch);

        log.info("Caducidad actualizada: batchId={}, anterior={}, nueva={}, motivo={}",
                batchId, previousDate, newExpirationDate, reason);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getActiveBatches(Integer productId) {
        return batchRepository.findByProductIdAndDepletedFalseOrderByExpirationDateAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getAllActiveBatches() {
        return batchRepository.findAllActiveBatchesOrderByExpiration();
    }

    @Transactional(readOnly = true)
    public Page<ProductBatch> findAllBatches(String search, Boolean depleted, Pageable pageable) {
        Specification<ProductBatch> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.join("product").get("name")), "%" + search.trim().toLowerCase() + "%"));
        }

        if (depleted != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("depleted"), depleted));
        }

        return batchRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<ProductBatch> getAllBatches(Integer productId) {
        return batchRepository.findByProductIdOrderByExpirationDateAsc(productId);
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
