package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class StockLedgerServiceLedgerMethodsTest {

        @Mock
        private I18nService i18nService;

        @Mock
        private StockLedgerRepository ledgerRepository;

        @Mock
        private StockSnapshotRepository snapshotRepository;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private RecipeCookingAuditRepository recipeCookingAuditRepository;

        @Mock
        private ProductBatchService productBatchService;

        @Mock
        private SecurityContextHelper securityContextHelper;

        @Mock
        private StockLedgerBatchDetailRepository batchDetailRepository;

        @Mock
        private ProductBatchRepository batchRepository;

        @Mock
        private Environment environment;

        @Mock
        private LedgerProperties ledgerProperties;

        @Mock
        private ApplicationEventPublisher applicationEventPublisher;

        private StockLedgerService stockLedgerService;

        private MeterRegistry meterRegistry;
        private Product testProduct1;
        private Product testProduct2;
        private User testUser;
        private List<StockLedger> ledgerEntries1;
        private List<StockLedger> ledgerEntries2;

        @BeforeEach
        void setUp() {
                meterRegistry = new SimpleMeterRegistry();
                
                // Recrear el servicio con el meterRegistry
                stockLedgerService = new StockLedgerService(
                        i18nService,
                        ledgerRepository,
                        snapshotRepository,
                        productRepository,
                        orderRepository,
                        recipeCookingAuditRepository,
                        productBatchService,
                        securityContextHelper,
                        batchDetailRepository,
                        batchRepository,
                        environment,
                        ledgerProperties,
                        applicationEventPublisher,
                        meterRegistry
                );

                lenient().when(ledgerProperties.getHmacSecret()).thenReturn("test-hmac-secret-for-ledger-integrity-verification");

                testUser = new User();
                testUser.setId(1);
                testUser.setName("Test User");

                testProduct1 = new Product();
                testProduct1.setId(1);
                testProduct1.setName("Product 1");

                testProduct2 = new Product();
                testProduct2.setId(2);
                testProduct2.setName("Product 2");

                // Crear ledger entries para producto 1
                StockLedger entry1 = StockLedger.builder()
                        .id(1L)
                        .product(testProduct1)
                        .sequenceNumber(1L)
                        .quantityDelta(new BigDecimal("100.000"))
                        .resultingStock(new BigDecimal("100.000"))
                        .movementType(MovementType.ENTRADA)
                        .description("Initial entry")
                        .transactionTimestamp(LocalDateTime.now())
                        .user(testUser)
                        .previousHash("GENESIS")
                        .currentHash("hash1_12345678")
                        .verified(true)
                        .build();

                StockLedger entry2 = StockLedger.builder()
                        .id(2L)
                        .product(testProduct1)
                        .sequenceNumber(2L)
                        .quantityDelta(new BigDecimal("-50.000"))
                        .resultingStock(new BigDecimal("50.000"))
                        .movementType(MovementType.SALIDA)
                        .description("Sale")
                        .transactionTimestamp(LocalDateTime.now())
                        .user(testUser)
                        .previousHash("hash1_12345678")
                        .currentHash("hash2_87654321")
                        .verified(true)
                        .build();

                ledgerEntries1 = Arrays.asList(entry1, entry2);

                // Crear ledger entries para producto 2
                StockLedger entry3 = StockLedger.builder()
                        .id(3L)
                        .product(testProduct2)
                        .sequenceNumber(1L)
                        .quantityDelta(new BigDecimal("50.000"))
                        .resultingStock(new BigDecimal("50.000"))
                        .movementType(MovementType.ENTRADA)
                        .description("Initial entry")
                        .transactionTimestamp(LocalDateTime.now())
                        .user(testUser)
                        .previousHash("GENESIS")
                        .currentHash("hash3_11112222")
                        .verified(true)
                        .build();

                ledgerEntries2 = Arrays.asList(entry3);
        }

        @Test
        void getProductsWithLedger_ReturnsDistinctProductIds() {
                when(ledgerRepository.findDistinctProductIds()).thenReturn(Arrays.asList(1, 2));

                List<Integer> result = stockLedgerService.getProductsWithLedger();

                assertNotNull(result);
                assertEquals(2, result.size());
                assertTrue(result.contains(1));
                assertTrue(result.contains(2));
                verify(ledgerRepository, times(1)).findDistinctProductIds();
        }

        @Test
        void getProductsWithLedger_WhenNoLedger_ReturnsEmptyList() {
                when(ledgerRepository.findDistinctProductIds()).thenReturn(Arrays.asList());

                List<Integer> result = stockLedgerService.getProductsWithLedger();

                assertNotNull(result);
                assertTrue(result.isEmpty());
                verify(ledgerRepository, times(1)).findDistinctProductIds();
        }

        @Test
        void verifyProductsWithLedger_VerifiesAllProductsWithLedger() {
                when(ledgerRepository.findDistinctProductIds()).thenReturn(Arrays.asList(1, 2));
                List<StockLedger> allEntries = new java.util.ArrayList<>();
                allEntries.addAll(ledgerEntries1);
                allEntries.addAll(ledgerEntries2);
                when(ledgerRepository.findByProductIdInOrderBySequenceNumber(anyList())).thenReturn(allEntries);

                List<IntegrityCheckResult> results = stockLedgerService.verifyProductsWithLedger();

                assertNotNull(results);
                assertEquals(2, results.size());
                
                // Verificar que se llamaron los métodos correctos
                verify(ledgerRepository, times(1)).findDistinctProductIds();
                verify(ledgerRepository, times(1)).findByProductIdInOrderBySequenceNumber(anyList());
        }

        @Test
        void verifyProductsWithLedger_WhenNoProducts_ReturnsEmptyList() {
                when(ledgerRepository.findDistinctProductIds()).thenReturn(Arrays.asList());

                List<IntegrityCheckResult> results = stockLedgerService.verifyProductsWithLedger();

                assertNotNull(results);
                assertTrue(results.isEmpty());
                verify(ledgerRepository, times(1)).findDistinctProductIds();
        }
}
