package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
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

        StockLedger ledger = new StockLedger();
        ledger.setId(5L);

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

        List<ProductBatch> affected = productBatchService.consumeStock(1, new BigDecimal("5.000"));

        assertEquals(new BigDecimal("0.000"), older.getRemainingQuantity());
        assertTrue(older.isDepleted());
        assertEquals(new BigDecimal("2.000"), newer.getRemainingQuantity());
        assertTrue(!newer.isDepleted());
        assertEquals(2, affected.size());
        verify(batchRepository).saveAll(affected);
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
}
