package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.application.dto.response.BatchTypeaheadDTO;
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
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;


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
        return createBatch(product, quantity, expirationDate, ledgerTx, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductBatch createBatch(Product product, BigDecimal quantity, LocalDate expirationDate, StockLedger ledgerTx,
            String batchCode) {
        if (expirationDate == null) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED));
        }
        if (expirationDate.isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST, new Object[]{expirationDate}));
        }
        BigDecimal normalizedQuantity = quantity.setScale(3, RoundingMode.HALF_UP);
        ProductBatch batch = ProductBatch.builder()
                .product(product)
                .expirationDate(expirationDate)
                .initialQuantity(normalizedQuantity)
                .remainingQuantity(normalizedQuantity)
                .receivedAt(LocalDateTime.now())
                .ledgerTransaction(ledgerTx)
                .depleted(false)
                .batchCode(batchCode)
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
                log.warn("FEFO: saltando lote caducado batchId={}, expiration={}", batch.getId(), batch.getExpirationDate());
                continue;
            }

            BigDecimal toConsume = remaining.min(batch.getRemainingQuantity());
            if (toConsume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal newRemaining = batch.getRemainingQuantity()
                    .subtract(toConsume)
                    .setScale(3, RoundingMode.HALF_UP);
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
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT,
                        new Object[]{batchId, batch.getProduct().getName(), batch.getExpirationDate()}));
        }

        if (batch.isDepleted() || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_BATCH_DEPLETED));
        }

        BigDecimal toConsume = quantity.min(batch.getRemainingQuantity());
        BigDecimal newRemaining = batch.getRemainingQuantity()
                .subtract(toConsume)
                .setScale(3, RoundingMode.HALF_UP);

        batch.setRemainingQuantity(newRemaining);
        if (batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            batch.setDepleted(true);
        }
        batchRepository.save(batch);

        if (quantity.compareTo(toConsume) > 0) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_BATCH_INSUFFICIENT_STOCK_ADJUSTMENT));
        }

        return List.of(new BatchConsumptionDetail(batch.getId(), toConsume));
    }

    /**
     * Agota un lote caducado directamente. Solo funciona si el lote está realmente caducado.
     * Este método existe para evitar exponer un flag "allowExpired" en consumeFromSpecificBatch.
     */
    @RealtimeSync(entityType = "batch", action = "STATUS_CHANGE", idFromArg = 0,
            affectedDomains = {"batch", "product", "weekly_plan", "stock_alerts"})
    @Transactional(rollbackFor = Exception.class)
    public void depleteExpiredBatch(Long batchId) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (batch.getExpirationDate() == null || !batch.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_NOT_EXPIRED));
        }

        if (batch.isDepleted() || batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_DEPLETED));
        }

        batch.setRemainingQuantity(BigDecimal.ZERO);
        batch.setDepleted(true);
        batchRepository.save(batch);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addStockToBatch(Long batchId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException(
                i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_ADD_STOCK, new Object[]{batchId}));
        }

        BigDecimal newRemaining = batch.getRemainingQuantity()
                .add(quantity)
                .setScale(3, RoundingMode.HALF_UP);

        log.info("Añadiendo stock al lote {}: anterior={}, añadir={}, nuevo={}, antes_depleted={}", 
                batchId, batch.getRemainingQuantity(), quantity, newRemaining, batch.isDepleted());

        batch.setRemainingQuantity(newRemaining);
        if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
            batch.setDepleted(false);
            log.info("Lote {} marcado como NO agotado", batchId);
        }
        batchRepository.saveAndFlush(batch);
    }

    @RealtimeSync(entityType = "batch", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"batch", "stock_alerts", "weekly_plan"})
    @Transactional(rollbackFor = Exception.class)
    public ProductBatch updateExpirationDate(Long batchId, LocalDate newExpirationDate, String reason) {
        return updateExpirationDate(batchId, newExpirationDate, reason, null);
    }

    @RealtimeSync(entityType = "batch", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"batch", "stock_alerts", "weekly_plan"})
    @Transactional(rollbackFor = Exception.class)
    public ProductBatch updateExpirationDate(Long batchId, LocalDate newExpirationDate, String reason, String batchCode) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

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
        if (batchCode != null) {
            batch.setBatchCode(batchCode);
        }
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
            String searchLower = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> 
                cb.or(
                    cb.like(cb.lower(root.join("product").get("name")), searchLower),
                    cb.like(cb.lower(root.get("batchCode")), searchLower)
                ));
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

    @Transactional(readOnly = true)
    public Optional<ProductBatch> findByBatchCode(String batchCode) {
        return batchRepository.findByBatchCode(batchCode);
    }

    @Transactional(readOnly = true)
    public Optional<ProductBatch> resolveByIdOrBatchCode(Integer productId, String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = reference.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            try {
                Long batchId = Long.parseLong(normalized);
                return batchRepository.findById(batchId)
                        .filter(batch -> productId == null || batch.getProduct().getId().equals(productId));
            } catch (NumberFormatException ignored) {
                // If parse fails for any reason, fallback to batch code lookup below.
            }
        }

        return batchRepository.findByBatchCode(normalized)
                .filter(batch -> productId == null || batch.getProduct().getId().equals(productId));
    }

    @Transactional(readOnly = true)
    public List<BatchTypeaheadDTO> getBatchTypeahead(String query, Integer productId, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 30));
        String normalized = query.trim().toLowerCase();
        boolean numericQuery = normalized.chars().allMatch(Character::isDigit);

        Stream<ProductBatch> stream = (productId != null
                ? batchRepository.findByProductIdOrderByExpirationDateAsc(productId)
                : batchRepository.findAllActiveBatchesOrderByExpiration())
                .stream();

        return stream
                .filter(batch -> matchesTypeahead(batch, normalized, numericQuery))
                .sorted(Comparator
                        .comparingInt((ProductBatch batch) -> rankTypeahead(batch, normalized, numericQuery))
                        .thenComparing(ProductBatch::getReceivedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .map(batch -> BatchTypeaheadDTO.builder()
                        .id(batch.getId())
                        .batchCode(batch.getBatchCode())
                        .productId(batch.getProduct().getId())
                        .productName(batch.getProduct().getName())
                        .expirationDate(batch.getExpirationDate())
                        .remainingQuantity(batch.getRemainingQuantity())
                        .build())
                .toList();
    }

    private boolean matchesTypeahead(ProductBatch batch, String normalized, boolean numericQuery) {
        String code = batch.getBatchCode() == null ? "" : batch.getBatchCode().toLowerCase();
        boolean codeMatch = !code.isEmpty() && code.contains(normalized);
        if (numericQuery) {
            return String.valueOf(batch.getId()).startsWith(normalized) || codeMatch;
        }
        return codeMatch;
    }

    private int rankTypeahead(ProductBatch batch, String normalized, boolean numericQuery) {
        String code = batch.getBatchCode() == null ? "" : batch.getBatchCode().toLowerCase();
        String idAsString = String.valueOf(batch.getId());

        if (!code.isEmpty() && code.equals(normalized)) {
            return 0;
        }
        if (numericQuery && idAsString.equals(normalized)) {
            return 1;
        }
        if (!code.isEmpty() && code.startsWith(normalized)) {
            return 2;
        }
        if (numericQuery && idAsString.startsWith(normalized)) {
            return 3;
        }
        return 4;
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
