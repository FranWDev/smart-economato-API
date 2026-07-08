package com.economato.inventory.application.usecase.crisis;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.RoleNotificationService;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.dto.shared.request.BatchMovementItem;
import com.economato.inventory.domain.model.order.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

import com.economato.inventory.application.dto.crisis.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.crisis.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.crisis.response.ForwardTraceabilityDTO;
import com.economato.inventory.application.dto.crisis.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.application.mapper.order.OrderMapper;
import com.economato.inventory.application.mapper.product.ProductBatchMapper;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import com.economato.inventory.domain.model.crisis.CrisisAffectedProduct;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.CrisisAffectedProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.FoodCrisisRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.order.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
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
    @Mock private ProductBatchRepository productBatchRepository;
    @Mock private ProductBatchMapper productBatchMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private PersistentNotificationService persistentNotificationService;
    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

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

        TraceabilityNotificationService notificationServ = new TraceabilityNotificationService(
            notificationService, persistentNotificationService, null, null, i18nService
        );
        CrisisContainmentService containmentService = new CrisisContainmentService(
            productRepository, orderRepository, ledgerRepository, cookingAuditRepository, supplierRepository,
            ledgerService, productBatchRepository, foodCrisisRepository, crisisAffectedProductRepository,
            notificationServ, securityContextHelper, i18nService, meterRegistry, objectMapper
        );
        TraceabilityQueryService queryService = new TraceabilityQueryService(
            productRepository, orderRepository, ledgerRepository, cookingAuditRepository, supplierRepository,
            productBatchRepository, null, orderMapper, cookingAuditMapper, ledgerMapper, productBatchMapper,
            objectMapper, i18nService
        );
        traceabilityService = new TraceabilityService(containmentService, queryService);
    }

    @Test
    void activateCrisis_ShouldSavePersistentState() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 31, 23, 59);
        CrisisActivationRequestDTO request = buildActivationRequest(from, to);

        mockCommonActivationDependencies(request, 99L);
        when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(any())).thenReturn(List.of());
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
        when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(List.of(1))).thenReturn(List.of(inRange, outOfRange, depleted, zeroQty));
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
        when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(List.of(1))).thenReturn(List.of(onlyOutOfRange));
        when(ledgerService.recordBatchStockMovements(anyList(), any(), any())).thenReturn(List.of(buildTx("hash-0")));

        traceabilityService.activateCrisis(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchMovementItem>> movementsCaptor = ArgumentCaptor
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
        when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(List.of(1))).thenReturn(List.of(atFrom, inMiddle, atTo, beforeFrom, afterTo));
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
        when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(List.of(1))).thenReturn(List.of(earlierExpiringButNotImplicated, implicated));
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
        when(crisisAffectedProductRepository.findByFoodCrisisIdWithProduct(7L)).thenReturn(List.of(association));
        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);

        traceabilityService.liftCrisis(request);

        assertEquals(new BigDecimal("85.00"), product.getAvailabilityPercentage());
        assertEquals(FoodCrisis.CrisisStatus.LIFTED, crisis.getStatus());
        verify(productRepository).saveAll(anyList());
        verify(foodCrisisRepository).save(crisis);
    }

    @Test
    void getForwardTraceability_ShouldUseSnapshotNotCurrentRecipe() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<Integer> productIds = List.of(1); // The "crisis" product
        
        RecipeCookingAudit matchingAudit = new RecipeCookingAudit();
        matchingAudit.setId(100L);
        matchingAudit.setCookingDate(now);
        matchingAudit.setComponentsState("{\"components\": [{\"productId\": 1}]}");

        RecipeCookingAudit nonMatchingAudit = new RecipeCookingAudit();
        nonMatchingAudit.setId(101L);
        nonMatchingAudit.setCookingDate(now);
        nonMatchingAudit.setComponentsState("{\"components\": [{\"productId\": 2}]}");

        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(productRepository.findAllById(productIds)).thenReturn(List.of(product));
        when(cookingAuditRepository.findByDateRange(any(), any())).thenReturn(List.of(matchingAudit, nonMatchingAudit));
        
        // Mock JSON parsing
        Map<String, Object> matchingState = Map.of("components", List.of(Map.of("productId", 1)));
        Map<String, Object> nonMatchingState = Map.of("components", List.of(Map.of("productId", 2)));
        
        when(objectMapper.readValue(eq(matchingAudit.getComponentsState()), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(matchingState);
        when(objectMapper.readValue(eq(nonMatchingAudit.getComponentsState()), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(nonMatchingState);
            
        // Stub Mappers to avoid NPE
        lenient().when(cookingAuditMapper.toResponseDTO(matchingAudit))
            .thenReturn(RecipeCookingAuditResponseDTO.builder().id(100L).build());
        lenient().when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(null);
        lenient().when(ledgerMapper.toDTO(any())).thenReturn(null);
        lenient().when(productBatchMapper.toResponseDTO(any())).thenReturn(null);

        ForwardTraceabilityDTO result = traceabilityService.getForwardTraceability(1, productIds, now.minusDays(1), now.plusDays(1));

        assertNotNull(result);
        assertEquals(1, result.getAffectedCookings().size());
        assertEquals(100L, result.getAffectedCookings().get(0).getId());
    }

    @Test
    void buildCrisisResponsesBatch_ShouldFilterCookingsBySurgicalProductList() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        
        // Crisis 1: Product 1
        FoodCrisis crisis1 = new FoodCrisis();
        crisis1.setId(1L);
        crisis1.setCrisisCode("C1");
        crisis1.setSupplier(supplier);
        crisis1.setDateFrom(now.minusDays(5));
        crisis1.setDateTo(now.plusDays(5));
        crisis1.setStatus(FoodCrisis.CrisisStatus.ACTIVE);

        // Crisis 2: Product 2
        FoodCrisis crisis2 = new FoodCrisis();
        crisis2.setId(2L);
        crisis2.setCrisisCode("C2");
        crisis2.setSupplier(supplier);
        crisis2.setDateFrom(now.minusDays(5));
        crisis2.setDateTo(now.plusDays(5));
        crisis2.setStatus(FoodCrisis.CrisisStatus.ACTIVE);

        Product product1 = new Product(); product1.setId(1); product1.setName("P1");
        Product product2 = new Product(); product2.setId(2); product2.setName("P2");

        CrisisAffectedProduct ap1 = new CrisisAffectedProduct(); ap1.setFoodCrisis(crisis1); ap1.setProduct(product1);
        CrisisAffectedProduct ap2 = new CrisisAffectedProduct(); ap2.setFoodCrisis(crisis2); ap2.setProduct(product2);

        when(crisisAffectedProductRepository.findByFoodCrisisIdIn(anyList())).thenReturn(List.of(ap1, ap2));
        
        RecipeCookingAudit auditForP1 = new RecipeCookingAudit();
        auditForP1.setId(10L);
        auditForP1.setCookingDate(now);
        auditForP1.setComponentsState("{\"p\": 1}");

        RecipeCookingAudit auditForP2 = new RecipeCookingAudit();
        auditForP2.setId(20L);
        auditForP2.setCookingDate(now);
        auditForP2.setComponentsState("{\"p\": 2}");

        when(foodCrisisRepository.findAllWithSupplier()).thenReturn(List.of(crisis1, crisis2));
        when(cookingAuditRepository.findByDateRange(any(), any())).thenReturn(List.of(auditForP1, auditForP2));
        when(orderRepository.findConfirmedOrdersByProductIdsAndDateRange(anyList(), any(), any())).thenReturn(List.of());

        // Mock JSON parsing specifically
        when(objectMapper.readValue(eq(auditForP1.getComponentsState()), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Map.of("components", List.of(Map.of("productId", 1))));
        when(objectMapper.readValue(eq(auditForP2.getComponentsState()), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Map.of("components", List.of(Map.of("productId", 2))));

        // Stub Cookings Mapper
        lenient().when(cookingAuditMapper.toResponseDTO(any(RecipeCookingAudit.class))).thenAnswer(inv -> {
            RecipeCookingAudit audit = inv.getArgument(0);
            return RecipeCookingAuditResponseDTO.builder()
                .id(audit.getId())
                .build();
        });

        List<CrisisResponseDTO> results = traceabilityService.getAllCrises(); // This calls buildCrisisResponsesBatch

        assertEquals(2, results.size());
        
        CrisisResponseDTO res1 = results.stream().filter(r -> r.getCrisisCode().equals("C1")).findFirst().get();
        assertEquals(1, res1.getAffectedCookings().size());
        assertEquals(10L, res1.getAffectedCookings().get(0).getCookingAuditId());

        CrisisResponseDTO res2 = results.stream().filter(r -> r.getCrisisCode().equals("C2")).findFirst().get();
        assertEquals(1, res2.getAffectedCookings().size());
        assertEquals(20L, res2.getAffectedCookings().get(0).getCookingAuditId());
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
        lenient().when(productBatchRepository.findByProductIdInOrderByExpirationDateAsc(any())).thenReturn(List.of());
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
        when(crisisAffectedProductRepository.findByFoodCrisisIdWithProduct(crisisId)).thenReturn(List.of(assoc));

        lenient().when(orderRepository.findConfirmedOrdersBySupplierAndProductIdsAndDateRange(any(), anyList(), any(), any()))
                .thenReturn(List.of());
        lenient().when(cookingAuditRepository.findByDateRange(any(), any()))
                .thenReturn(List.of());
        when(ledgerService.verifyChainIntegrityBatch(anyList()))
                .thenReturn(List.of(new IntegrityCheckResult(1, "Test Product", true, "ok", List.of())));
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
        return StockLedger.builder()
                .product(product)
                .currentHash(hash)
                .build();
    }
}