package com.economato.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.dto.response.IntegrityCheckResult;
import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.StockSnapshotRepository;
import com.economato.inventory.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        private SecurityContextHelper securityContextHelper;

        @Mock
        private Environment environment;

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
                        securityContextHelper,
                        environment,
                        meterRegistry
                );

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
                StockLedger entry1 = new StockLedger();
                entry1.setId(1L);
                entry1.setProduct(testProduct1);
                entry1.setSequenceNumber(1L);
                entry1.setQuantityDelta(new BigDecimal("100.000"));
                entry1.setResultingStock(new BigDecimal("100.000"));
                entry1.setMovementType(MovementType.ENTRADA);
                entry1.setDescription("Initial entry");
                entry1.setTransactionTimestamp(LocalDateTime.now());
                entry1.setUser(testUser);
                entry1.setPreviousHash("GENESIS");
                entry1.setCurrentHash("hash1_12345678");
                entry1.setVerified(true);

                StockLedger entry2 = new StockLedger();
                entry2.setId(2L);
                entry2.setProduct(testProduct1);
                entry2.setSequenceNumber(2L);
                entry2.setQuantityDelta(new BigDecimal("-50.000"));
                entry2.setResultingStock(new BigDecimal("50.000"));
                entry2.setMovementType(MovementType.SALIDA);
                entry2.setDescription("Sale");
                entry2.setTransactionTimestamp(LocalDateTime.now());
                entry2.setUser(testUser);
                entry2.setPreviousHash("hash1_12345678");
                entry2.setCurrentHash("hash2_87654321");
                entry2.setVerified(true);

                ledgerEntries1 = Arrays.asList(entry1, entry2);

                // Crear ledger entries para producto 2
                StockLedger entry3 = new StockLedger();
                entry3.setId(3L);
                entry3.setProduct(testProduct2);
                entry3.setSequenceNumber(1L);
                entry3.setQuantityDelta(new BigDecimal("50.000"));
                entry3.setResultingStock(new BigDecimal("50.000"));
                entry3.setMovementType(MovementType.ENTRADA);
                entry3.setDescription("Initial entry");
                entry3.setTransactionTimestamp(LocalDateTime.now());
                entry3.setUser(testUser);
                entry3.setPreviousHash("GENESIS");
                entry3.setCurrentHash("hash3_11112222");
                entry3.setVerified(true);

                ledgerEntries2 = Arrays.asList(entry3);
        }

        @Test
        void getProductsWithLedger_ReturnsDistinctProductIds() {
                // Combinar todas las entradas
                List<StockLedger> allEntries = Arrays.asList(
                        ledgerEntries1.get(0), ledgerEntries1.get(1), ledgerEntries2.get(0)
                );

                when(ledgerRepository.findAll()).thenReturn(allEntries);

                List<Integer> result = stockLedgerService.getProductsWithLedger();

                assertNotNull(result);
                assertEquals(2, result.size());
                assertTrue(result.contains(1));
                assertTrue(result.contains(2));
                verify(ledgerRepository, times(1)).findAll();
        }

        @Test
        void getProductsWithLedger_WhenNoLedger_ReturnsEmptyList() {
                when(ledgerRepository.findAll()).thenReturn(Arrays.asList());

                List<Integer> result = stockLedgerService.getProductsWithLedger();

                assertNotNull(result);
                assertTrue(result.isEmpty());
                verify(ledgerRepository, times(1)).findAll();
        }

        @Test
        void verifyProductsWithLedger_VerifiesAllProductsWithLedger() {
                // Combinar todas las entradas
                List<StockLedger> allEntries = Arrays.asList(
                        ledgerEntries1.get(0), ledgerEntries1.get(1), ledgerEntries2.get(0)
                );

                when(ledgerRepository.findAll()).thenReturn(allEntries);
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct1));
                when(productRepository.findById(2)).thenReturn(java.util.Optional.of(testProduct2));
                when(ledgerRepository.findByProductIdOrderBySequenceNumber(1)).thenReturn(ledgerEntries1);
                when(ledgerRepository.findByProductIdOrderBySequenceNumber(2)).thenReturn(ledgerEntries2);

                List<IntegrityCheckResult> results = stockLedgerService.verifyProductsWithLedger();

                assertNotNull(results);
                assertEquals(2, results.size());
                
                // Verificar que se llamaron los métodos correctos
                verify(ledgerRepository, times(1)).findAll();
                verify(ledgerRepository, times(1)).findByProductIdOrderBySequenceNumber(1);
                verify(ledgerRepository, times(1)).findByProductIdOrderBySequenceNumber(2);
        }

        @Test
        void verifyProductsWithLedger_WhenNoProducts_ReturnsEmptyList() {
                when(ledgerRepository.findAll()).thenReturn(Arrays.asList());

                List<IntegrityCheckResult> results = stockLedgerService.verifyProductsWithLedger();

                assertNotNull(results);
                assertTrue(results.isEmpty());
                verify(ledgerRepository, times(1)).findAll();
        }
}
