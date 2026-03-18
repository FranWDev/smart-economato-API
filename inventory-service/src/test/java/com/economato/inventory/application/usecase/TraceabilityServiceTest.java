package com.economato.inventory.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.CrisisAffectedProduct;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.Supplier;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.CrisisAffectedProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
public class TraceabilityServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockLedgerRepository ledgerRepository;
    @Mock private RecipeCookingAuditRepository cookingAuditRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private StockLedgerService ledgerService;
    @Mock private ProductBatchService productBatchService;
    @Mock private RoleNotificationService notificationService;
    @Mock private I18nService i18nService;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private OrderMapper orderMapper;
    @Mock private RecipeCookingAuditMapper cookingAuditMapper;
    @Mock private StockLedgerMapper ledgerMapper;
    @Mock private FoodCrisisRepository foodCrisisRepository;
    @Mock private CrisisAffectedProductRepository crisisAffectedProductRepository;
    @Mock private ObjectMapper objectMapper;
    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private TraceabilityService traceabilityService;

    private Product product;
    private Supplier supplier;
    private User admin;

    @BeforeEach
    void setUp() {
        supplier = new Supplier();
        supplier.setId(1);
        supplier.setName("Test Supplier");

        product = new Product();
        product.setId(1);
        product.setName("Test Product");
        product.setAvailabilityPercentage(new BigDecimal("100.00"));

        admin = new User();
        admin.setId(1);
        admin.setUser("admin");

        lenient().when(i18nService.getMessage(any(MessageKey.class)))
            .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
        lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());
    }

    @Test
    void activateCrisis_ShouldSavePersistentState() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 31, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        mockCommonActivationDependencies(request, 99L);
        when(productBatchService.getAllBatches(1)).thenReturn(List.of());
        when(ledgerService.recordBatchStockMovements(anyList(), any(), any())).thenReturn(List.of(buildTx("hash-1")));

        CrisisResponseDTO response = traceabilityService.activateCrisis(request);

        assertNotNull(response);
        assertEquals(99L, response.getCrisisId());
        verify(productRepository).updateAvailabilityForProducts(eq(List.of(1)), eq(BigDecimal.ZERO));
        verify(foodCrisisRepository).save(any(FoodCrisis.class));
        verify(crisisAffectedProductRepository).saveAll(anyList());
    }

        @Test
        void activateCrisis_ShouldQuarantineOnlyBatchesWithinDateRange() {
        LocalDateTime from = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 2, 28, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        ProductBatch inRange = buildBatch(10L, new BigDecimal("5.500"), LocalDateTime.of(2026, 2, 10, 12, 0),
            false);
        ProductBatch outOfRange = buildBatch(11L, new BigDecimal("9.000"), LocalDateTime.of(2026, 3, 2, 12, 0),
            false);
        ProductBatch depleted = buildBatch(12L, new BigDecimal("4.000"), LocalDateTime.of(2026, 2, 12, 12, 0),
            true);
        ProductBatch zeroQty = buildBatch(13L, BigDecimal.ZERO, LocalDateTime.of(2026, 2, 13, 12, 0), false);

        mockCommonActivationDependencies(request, 100L);
        when(productBatchService.getAllBatches(1)).thenReturn(List.of(inRange, outOfRange, depleted, zeroQty));
        when(ledgerService.recordManualAdjustment(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildTx("hash-manual"));

        traceabilityService.activateCrisis(request);

        ArgumentCaptor<Integer> productIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> deltaCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<MovementType> typeCaptor = ArgumentCaptor.forClass(MovementType.class);
        ArgumentCaptor<Long> batchIdCaptor = ArgumentCaptor.forClass(Long.class);

        verify(ledgerService, times(1)).recordManualAdjustment(
            productIdCaptor.capture(),
            deltaCaptor.capture(),
            typeCaptor.capture(),
            any(),
            eq(admin),
            batchIdCaptor.capture(),
            any());

        assertEquals(1, productIdCaptor.getValue());
        assertEquals(0, deltaCaptor.getValue().compareTo(new BigDecimal("-5.500")));
        assertEquals(MovementType.CUARENTENA, typeCaptor.getValue());
        assertEquals(10L, batchIdCaptor.getValue());

        verify(ledgerService, never()).recordBatchStockMovements(anyList(), any(), any());
        }

        @Test
        void activateCrisis_ShouldCreateZeroDeltaMarkerWhenNoBatchesInRange() {
        LocalDateTime from = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 30, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        ProductBatch onlyOutOfRange = buildBatch(21L, new BigDecimal("3.000"), LocalDateTime.of(2026, 5, 1, 10, 0),
            false);

        mockCommonActivationDependencies(request, 101L);
        when(productBatchService.getAllBatches(1)).thenReturn(List.of(onlyOutOfRange));
        when(ledgerService.recordBatchStockMovements(anyList(), any(), any())).thenReturn(List.of(buildTx("hash-0")));

        traceabilityService.activateCrisis(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.economato.inventory.application.dto.request.BatchMovementItem>> movementsCaptor = ArgumentCaptor
            .forClass(List.class);

        verify(ledgerService, times(1)).recordBatchStockMovements(movementsCaptor.capture(), eq(admin), eq(null));
        verify(ledgerService, never()).recordManualAdjustment(any(), any(), any(), any(), any(), any(), any());

        var movements = movementsCaptor.getValue();
        assertEquals(1, movements.size());
        assertEquals(0, movements.get(0).getQuantityDelta().compareTo(BigDecimal.ZERO));
        assertEquals(MovementType.CUARENTENA, movements.get(0).getMovementType());
        }

        @Test
        void activateCrisis_ShouldIncludeOnlyBatchesInRangeInResponse_WithInclusiveBoundaries() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 30, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        ProductBatch atFrom = buildBatch(31L, new BigDecimal("1.000"), from, false);
        ProductBatch inMiddle = buildBatch(32L, new BigDecimal("2.000"), LocalDateTime.of(2026, 6, 15, 12, 0), false);
        ProductBatch atTo = buildBatch(33L, new BigDecimal("3.000"), to, false);
        ProductBatch beforeFrom = buildBatch(34L, new BigDecimal("4.000"), from.minusSeconds(1), false);
        ProductBatch afterTo = buildBatch(35L, new BigDecimal("5.000"), to.plusSeconds(1), false);

        mockCommonActivationDependencies(request, 102L);
        when(productBatchService.getAllBatches(1)).thenReturn(List.of(atFrom, inMiddle, atTo, beforeFrom, afterTo));
        when(ledgerService.recordManualAdjustment(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildTx("hash-ranged"));

        CrisisResponseDTO response = traceabilityService.activateCrisis(request);

        assertNotNull(response.getAffectedBatches());
        assertEquals(3, response.getAffectedBatches().size());

        List<Long> ids = response.getAffectedBatches().stream().map(b -> b.getBatchId()).toList();
        assertTrue(ids.contains(31L));
        assertTrue(ids.contains(32L));
        assertTrue(ids.contains(33L));
        assertFalse(ids.contains(34L));
        assertFalse(ids.contains(35L));
        }

    @Test
    void activateCrisis_ShouldNotConsumeEarlierExpiringNonImplicatedBatch_FEFORegression() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 20, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        ProductBatch earlierExpiringButNotImplicated = buildBatch(
            41L,
            new BigDecimal("10.000"),
            LocalDateTime.of(2026, 7, 1, 9, 0),
            false);
        earlierExpiringButNotImplicated.setExpirationDate(LocalDate.of(2026, 7, 25));

        ProductBatch implicated = buildBatch(
            42L,
            new BigDecimal("2.500"),
            LocalDateTime.of(2026, 7, 15, 9, 0),
            false);
        implicated.setExpirationDate(LocalDate.of(2026, 8, 10));

        mockCommonActivationDependencies(request, 103L);
        when(productBatchService.getAllBatches(1)).thenReturn(List.of(earlierExpiringButNotImplicated, implicated));
        when(ledgerService.recordManualAdjustment(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildTx("hash-fefo-regression"));

        traceabilityService.activateCrisis(request);

        ArgumentCaptor<Long> batchIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> deltaCaptor = ArgumentCaptor.forClass(BigDecimal.class);

        verify(ledgerService, times(1)).recordManualAdjustment(
            eq(1),
            deltaCaptor.capture(),
            eq(MovementType.CUARENTENA),
            any(),
            eq(admin),
            batchIdCaptor.capture(),
            any());

        assertEquals(42L, batchIdCaptor.getValue());
        assertEquals(0, deltaCaptor.getValue().compareTo(new BigDecimal("-2.500")));
    }

    @Test
    void liftCrisis_ShouldRestoreOriginalState() {
        CrisisLiftRequestDTO request = new CrisisLiftRequestDTO();
        request.setCrisisId(7L);

        FoodCrisis crisis = new FoodCrisis();
        crisis.setId(7L);
        crisis.setCrisisCode("CRISIS-TEST");
        crisis.setStatus(FoodCrisis.CrisisStatus.ACTIVE);

        CrisisAffectedProduct association = new CrisisAffectedProduct();
        association.setProduct(product);
        association.setFoodCrisis(crisis);
        association.setOriginalAvailabilityPercentage(new BigDecimal("85.00"));

        product.setAvailabilityPercentage(BigDecimal.ZERO);

        when(foodCrisisRepository.findById(7L)).thenReturn(Optional.of(crisis));
        when(crisisAffectedProductRepository.findByFoodCrisisId(7L)).thenReturn(List.of(association));
        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);

        traceabilityService.liftCrisis(request);

        assertEquals(new BigDecimal("85.00"), product.getAvailabilityPercentage());
        assertEquals(FoodCrisis.CrisisStatus.LIFTED, crisis.getStatus());
        verify(productRepository).saveAll(anyList());
        verify(foodCrisisRepository).save(crisis);
    }

    private CrisisActivationRequestDTO buildActivationRequest(LocalDateTime from, LocalDateTime to) {
        CrisisActivationRequestDTO request = new CrisisActivationRequestDTO();
        request.setSupplierId(1);
        request.setProductIds(List.of(1));
        request.setReason("Contamination");
        request.setDateFrom(from);
        request.setDateTo(to);
        return request;
    }

    private void mockCommonActivationDependencies(CrisisActivationRequestDTO request, Long crisisId) {
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(foodCrisisRepository.save(any(FoodCrisis.class))).thenAnswer(i -> {
            FoodCrisis crisis = i.getArgument(0);
            if (crisis.getId() == null) {
                crisis.setId(crisisId);
            }
            return crisis;
        });

        CrisisAffectedProduct assoc = new CrisisAffectedProduct();
        assoc.setFoodCrisis(FoodCrisis.builder().id(crisisId).dateFrom(request.getDateFrom()).dateTo(request.getDateTo()).build());
        assoc.setProduct(product);
        assoc.setOriginalAvailabilityPercentage(new BigDecimal("100.00"));
        when(crisisAffectedProductRepository.findByFoodCrisisId(crisisId)).thenReturn(List.of(assoc));

        when(orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(cookingAuditRepository.findAffectedCookingsByProductIdsAndDateRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(ledgerService.verifyChainIntegrity(1))
                .thenReturn(new IntegrityCheckResult(1, "Test Product", true, "ok", List.of()));
    }

    private ProductBatch buildBatch(Long id, BigDecimal remainingQty, LocalDateTime receivedAt, boolean depleted) {
        ProductBatch batch = new ProductBatch();
        batch.setId(id);
        batch.setProduct(product);
        batch.setExpirationDate(LocalDate.now().plusDays(10));
        batch.setInitialQuantity(remainingQty.max(BigDecimal.ZERO));
        batch.setRemainingQuantity(remainingQty);
        batch.setReceivedAt(receivedAt);
        batch.setDepleted(depleted);
        return batch;
    }

    private StockLedger buildTx(String hash) {
        StockLedger tx = new StockLedger();
        tx.setProduct(product);
        tx.setCurrentHash(hash);
        return tx;
    }
}
