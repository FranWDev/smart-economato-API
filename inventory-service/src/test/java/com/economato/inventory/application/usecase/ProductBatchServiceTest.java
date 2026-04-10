package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.application.dto.BatchConsumptionDetail;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductBatchServiceTest {

    @Mock
    private ProductBatchRepository batchRepository;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private ProductBatchService productBatchService;

    @Test
    void createBatch_shouldPersistWithInitialAndRemainingQuantity() {
        Product product = new Product();
        product.setId(1);

        StockLedger ledger = StockLedger.builder()
                .id(5L)
                .build();

        ProductBatch persisted = ProductBatch.builder()
                .id(10L)
                .product(product)
                .initialQuantity(new BigDecimal("15.000"))
                .remainingQuantity(new BigDecimal("15.000"))
                .expirationDate(LocalDate.now().plusDays(10))
                .depleted(false)
                .build();

        when(batchRepository.save(any(ProductBatch.class))).thenReturn(persisted);

        ProductBatch result = productBatchService.createBatch(
                product,
                new BigDecimal("15.000"),
                LocalDate.now().plusDays(10),
                ledger);

        assertEquals(10L, result.getId());
        assertEquals(new BigDecimal("15.000"), result.getInitialQuantity());
        assertEquals(new BigDecimal("15.000"), result.getRemainingQuantity());
        assertTrue(!result.isDepleted());
    }

    @Test
    void consumeStock_shouldApplyFefoAndDepleteOlderBatchFirst() {
        Product product = new Product();
        product.setId(1);

        ProductBatch older = ProductBatch.builder()
                .id(1L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(1))
                .remainingQuantity(new BigDecimal("3.000"))
                .initialQuantity(new BigDecimal("3.000"))
                .depleted(false)
                .build();

        ProductBatch newer = ProductBatch.builder()
                .id(2L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(5))
                .remainingQuantity(new BigDecimal("4.000"))
                .initialQuantity(new BigDecimal("4.000"))
                .depleted(false)
                .build();

        when(batchRepository.findActiveByProductIdOrderByExpiration(1)).thenReturn(List.of(older, newer));

        List<BatchConsumptionDetail> affected = productBatchService.consumeStock(1, new BigDecimal("5.000"));

        assertEquals(new BigDecimal("0.000"), older.getRemainingQuantity());
        assertTrue(older.isDepleted());
        assertEquals(new BigDecimal("2.000"), newer.getRemainingQuantity());
        assertTrue(!newer.isDepleted());
        assertEquals(2, affected.size());
        verify(batchRepository).saveAll(any());
    }

    @Test
    void consumeStock_shouldFailWhenNotEnoughBatches() {
        Product product = new Product();
        product.setId(1);

        ProductBatch onlyBatch = ProductBatch.builder()
                .id(1L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(2))
                .remainingQuantity(new BigDecimal("1.000"))
                .initialQuantity(new BigDecimal("1.000"))
                .depleted(false)
                .build();

        when(i18nService.getMessage(MessageKey.ERROR_BATCH_INSUFFICIENT_STOCK)).thenReturn("Insufficient stock in batches");
        when(batchRepository.findActiveByProductIdOrderByExpiration(1)).thenReturn(List.of(onlyBatch));

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.consumeStock(1, new BigDecimal("2.000")));
    }

    @Test
    void consumeStock_shouldFailIfFirstEligibleBatchIsExpired() {
        Product product = new Product();
        product.setId(1);

        ProductBatch expired = ProductBatch.builder()
                .id(1L)
                .product(product)
                .expirationDate(LocalDate.now().minusDays(1))
                .remainingQuantity(new BigDecimal("3.000"))
                .initialQuantity(new BigDecimal("3.000"))
                .depleted(false)
                .build();

        when(i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRED)).thenReturn("Batch expired");
        when(batchRepository.findActiveByProductIdOrderByExpiration(1)).thenReturn(List.of(expired));

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.consumeStock(1, new BigDecimal("1.000")));
    }

    // ===== Tests para updateExpirationDate =====

    @Test
    void updateExpirationDate_shouldUpdateSuccessfully() {
        Product product = new Product();
        product.setId(1);

        ProductBatch batch = ProductBatch.builder()
                .id(10L)
                .product(product)
                .expirationDate(LocalDate.now().plusDays(5))
                .initialQuantity(new BigDecimal("10.000"))
                .remainingQuantity(new BigDecimal("10.000"))
                .depleted(false)
                .build();

        LocalDate newDate = LocalDate.now().plusDays(30);
        ProductBatch updated = ProductBatch.builder()
                .id(10L).product(product).expirationDate(newDate)
                .initialQuantity(new BigDecimal("10.000"))
                .remainingQuantity(new BigDecimal("10.000"))
                .depleted(false).build();

        when(batchRepository.findById(10L)).thenReturn(java.util.Optional.of(batch));
        when(batchRepository.save(any(ProductBatch.class))).thenReturn(updated);

        ProductBatch result = productBatchService.updateExpirationDate(10L, newDate, "Corrección de fecha");

        assertEquals(newDate, result.getExpirationDate());
    }

    @Test
    void updateExpirationDate_shouldFail_whenBatchDepleted() {
        Product product = new Product();
        product.setId(1);

        ProductBatch depleted = ProductBatch.builder()
                .id(20L).product(product)
                .expirationDate(LocalDate.now().plusDays(10))
                .initialQuantity(new BigDecimal("5.000"))
                .remainingQuantity(BigDecimal.ZERO)
                .depleted(true).build();

        when(batchRepository.findById(20L)).thenReturn(java.util.Optional.of(depleted));
        when(i18nService.getMessage(MessageKey.ERROR_BATCH_DEPLETED_CANNOT_UPDATE))
                .thenReturn("Cannot update depleted batch");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.updateExpirationDate(20L, LocalDate.now().plusDays(30), null));
    }

    @Test
    void updateExpirationDate_shouldFail_whenDateIsInPast() {
        Product product = new Product();
        product.setId(1);

        ProductBatch batch = ProductBatch.builder()
                .id(30L).product(product)
                .expirationDate(LocalDate.now().plusDays(5))
                .initialQuantity(new BigDecimal("5.000"))
                .remainingQuantity(new BigDecimal("5.000"))
                .depleted(false).build();

        LocalDate pastDate = LocalDate.now().minusDays(1);

        when(batchRepository.findById(30L)).thenReturn(java.util.Optional.of(batch));
        when(i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_PAST, new Object[]{pastDate}))
                .thenReturn("Expiration date cannot be in the past");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.updateExpirationDate(30L, pastDate, "Test"));
    }

    @Test
    void updateExpirationDate_shouldFail_whenDateIsNull() {
        Product product = new Product();
        product.setId(1);

        ProductBatch batch = ProductBatch.builder()
                .id(40L).product(product)
                .expirationDate(LocalDate.now().plusDays(5))
                .initialQuantity(new BigDecimal("5.000"))
                .remainingQuantity(new BigDecimal("5.000"))
                .depleted(false).build();

        when(batchRepository.findById(40L)).thenReturn(java.util.Optional.of(batch));
        when(i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED))
                .thenReturn("Expiration date required");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.updateExpirationDate(40L, null, "Test"));
    }

    @Test
    void updateExpirationDate_shouldFail_whenBatchNotFound() {
        when(batchRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThrows(com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException.class,
                () -> productBatchService.updateExpirationDate(999L, LocalDate.now().plusDays(10), null));
    }

    @Test
    void consumeFromSpecificBatch_shouldFail_whenBatchIsExpired() {
        Product product = new Product();
        product.setId(1);

        ProductBatch expired = ProductBatch.builder()
                .id(5L).product(product)
                .expirationDate(LocalDate.now().minusDays(2))
                .initialQuantity(new BigDecimal("5.000"))
                .remainingQuantity(new BigDecimal("5.000"))
                .depleted(false).build();

        when(batchRepository.findById(5L)).thenReturn(java.util.Optional.of(expired));
        when(i18nService.getMessage(
                eq(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_REVERT), any(Object[].class)))
                .thenReturn("Batch expired cannot revert");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.consumeFromSpecificBatch(5L, new BigDecimal("3.000")));
    }

    @Test
    void addStockToBatch_shouldFail_whenBatchIsExpired() {
        Product product = new Product();
        product.setId(1);

        ProductBatch expired = ProductBatch.builder()
                .id(6L).product(product)
                .expirationDate(LocalDate.now().minusDays(1))
                .initialQuantity(new BigDecimal("3.000"))
                .remainingQuantity(new BigDecimal("3.000"))
                .depleted(false).build();

        when(batchRepository.findById(6L)).thenReturn(java.util.Optional.of(expired));
        when(i18nService.getMessage(
                eq(MessageKey.ERROR_BATCH_EXPIRED_CANNOT_ADD_STOCK), any(Object[].class)))
                .thenReturn("Batch expired cannot add stock");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.addStockToBatch(6L, new BigDecimal("2.000")));
    }

    @Test
    void createBatch_shouldFail_whenExpirationDateIsNull() {
        Product product = new Product();
        product.setId(1);

        when(i18nService.getMessage(MessageKey.ERROR_BATCH_EXPIRATION_REQUIRED))
                .thenReturn("Expiration date required");

        assertThrows(InvalidOperationException.class,
                () -> productBatchService.createBatch(
                        product, new BigDecimal("5.000"), null, null));
    }
}
