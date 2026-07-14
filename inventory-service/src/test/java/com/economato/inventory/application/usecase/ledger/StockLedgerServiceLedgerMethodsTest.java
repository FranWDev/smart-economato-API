package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerBatchDetailRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.blockchain.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
        private BlockchainProperties blockchainProperties;

        @Mock
        private LedgerChainVerificationService ledgerChainVerificationService;

        @Mock
        private ApplicationEventPublisher applicationEventPublisher;

        @Mock
        private WeeklyPlanStockReservationService weeklyPlanStockReservationService;

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
                
                StockMovementRecorder stockMovementRecorder = new StockMovementRecorder(
                        i18nService,
                        ledgerRepository,
                        snapshotRepository,
                        productRepository,
                        productBatchService,
                        batchDetailRepository,
                        batchRepository,
                        environment,
                        ledgerProperties,
                        applicationEventPublisher,
                        weeklyPlanStockReservationService,
                        securityContextHelper,
                        meterRegistry
                );

                StockReversalProcessor stockReversalProcessor = new StockReversalProcessor(
                        i18nService,
                        ledgerRepository,
                        snapshotRepository,
                        securityContextHelper,
                        batchDetailRepository,
                        batchRepository,
                        stockMovementRecorder,
                        orderRepository,
                        recipeCookingAuditRepository
                );

                StockLedgerIntegrityVerifier stockLedgerIntegrityVerifier = new StockLedgerIntegrityVerifier(
                        i18nService,
                        productRepository,
                        ledgerRepository,
                        ledgerChainVerificationService,
                        stockMovementRecorder,
                        snapshotRepository,
                        batchRepository
                );

                stockLedgerService = new StockLedgerService(
                        stockMovementRecorder,
                        stockReversalProcessor,
                        stockLedgerIntegrityVerifier,
                        ledgerRepository,
                        snapshotRepository,
                        productRepository,
                        securityContextHelper,
                        i18nService,
                        mock(StockLedgerMapper.class)
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
                List<StockLedger> allEntries = new ArrayList<>();
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

        @Test
        void verifyChainIntegrity_WhenChainEnabled_DelegatesToChainService() {
                when(productRepository.findById(1)).thenReturn(Optional.of(testProduct1));
                when(ledgerChainVerificationService.verifyLedgerChainIntegrity(1))
                        .thenReturn(List.of());
                when(ledgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(ledgerEntries1);
                when(i18nService.getMessage(eq(MessageKey.LEDGER_INTEGRITY_VALID), any(Object[].class)))
                        .thenReturn("Cadena íntegra");

                IntegrityCheckResult result = stockLedgerService.verifyChainIntegrity(1);

                assertTrue(result.isValid());
                verify(ledgerChainVerificationService, times(1)).verifyLedgerChainIntegrity(1);
                verify(ledgerRepository, times(1)).findByProductIdOrderBySequenceNumber(1);
        }

        @Test
        void verifyChainIntegrity_WhenChainReturnsErrors_ReturnsInvalid() {
                when(productRepository.findById(1)).thenReturn(Optional.of(testProduct1));
                when(ledgerChainVerificationService.verifyLedgerChainIntegrity(1))
                        .thenReturn(List.of("Hash corruption at sequence 1"));
                when(ledgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(ledgerEntries1);
                when(i18nService.getMessage(eq(MessageKey.LEDGER_INTEGRITY_CORRUPTED), any(Object[].class)))
                        .thenReturn("CORRUPCION");

                IntegrityCheckResult result = stockLedgerService.verifyChainIntegrity(1);

                assertFalse(result.isValid());
                verify(ledgerChainVerificationService, times(1)).verifyLedgerChainIntegrity(1);
                verify(ledgerRepository, times(1)).findByProductIdOrderBySequenceNumber(1);
        }
}
