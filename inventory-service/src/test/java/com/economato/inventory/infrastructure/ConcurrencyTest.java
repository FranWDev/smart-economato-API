package com.economato.inventory.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.economato.inventory.application.dto.request.ProductRequestDTO;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.application.usecase.ProductService;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrencyTest {

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldHandleOptimisticLockingConflict() throws InterruptedException {

        Product product = new Product();
        product.setName("Producto Test Concurrencia");
        product.setUnit("kg");
        product.setUnitPrice(BigDecimal.valueOf(10.00));
        product.setProductCode("TEST-CONCURRENCY-001");
        product.setCurrentStock(BigDecimal.valueOf(100));
        product = productRepository.save(product);

        final Integer productId = product.getId();
        final int threadCount = 5;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    ProductRequestDTO updateRequest = new ProductRequestDTO();
                    updateRequest.setName("Producto Test Concurrencia");
                    updateRequest.setUnit("kg");
                    updateRequest.setUnitPrice(BigDecimal.valueOf(10.00 + threadNum));
                    updateRequest.setProductCode("TEST-CONCURRENCY-001");
                    updateRequest.setCurrentStock(BigDecimal.valueOf(100 + threadNum));

                    productService.update(productId, updateRequest);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(successCount.get() > 0, "Al menos una actualización debe tener éxito");

        productRepository.deleteById(productId);
    }

    @Test
    void shouldIncrementVersionOnUpdate() {

        Product product = new Product();
        product.setName("Producto Test Version");
        product.setUnit("kg");
        product.setUnitPrice(BigDecimal.valueOf(20.00));
        product.setProductCode("TEST-VERSION-001");
        product.setCurrentStock(BigDecimal.valueOf(50));
        product = productRepository.save(product);

        Long initialVersion = product.getVersion();
        assertNotNull(initialVersion, "Version inicial debe ser establecida");

        product.setCurrentStock(BigDecimal.valueOf(75));
        product = productRepository.save(product);

        assertNotNull(product.getVersion());
        assertEquals(initialVersion + 1, product.getVersion(),
                "Version debe incrementarse en 1 después de actualización");

        productRepository.deleteById(product.getId());
    }
}
