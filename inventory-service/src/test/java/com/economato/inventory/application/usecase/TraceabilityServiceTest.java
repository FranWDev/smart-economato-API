package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TraceabilityServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockLedgerRepository ledgerRepository;
    @Mock private RecipeCookingAuditRepository cookingAuditRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private StockLedgerService ledgerService;
    @Mock private RoleNotificationService notificationService;
    @Mock private I18nService i18nService;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private OrderMapper orderMapper;
    @Mock private RecipeCookingAuditMapper cookingAuditMapper;
    @Mock private StockLedgerMapper ledgerMapper;
    @Mock private FoodCrisisRepository foodCrisisRepository;
    @Mock private CrisisAffectedProductRepository crisisAffectedProductRepository;
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
    }

    @Test
    void activateCrisis_ShouldSavePersistentState() {
        // Arrange
        CrisisActivationRequestDTO request = new CrisisActivationRequestDTO();
        request.setSupplierId(1);
        request.setProductIds(List.of(1));
        request.setReason("Contamination");
        request.setDateFrom(LocalDateTime.now().minusDays(1));
        request.setDateTo(LocalDateTime.now());

        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(foodCrisisRepository.save(any(FoodCrisis.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerService.recordBatchStockMovements(anyList(), any(), any())).thenReturn(List.of());
        when(ledgerService.verifyChainIntegrity(anyInt())).thenReturn(new IntegrityCheckResult(1, "Test Product", true, "OK", null));

        // Act
        CrisisResponseDTO response = traceabilityService.activateCrisis(request);

        // Assert
        assertNotNull(response);
        verify(productRepository).updateAvailabilityForProducts(eq(List.of(1)), eq(BigDecimal.ZERO));
        verify(foodCrisisRepository).save(argThat(c -> c.getStatus() == FoodCrisis.CrisisStatus.ACTIVE));
        verify(crisisAffectedProductRepository).saveAll(anyList());
    }

    @Test
    void liftCrisis_ShouldRestoreOriginalState() {
        // Arrange
        CrisisLiftRequestDTO request = new CrisisLiftRequestDTO();
        request.setProductIds(List.of(1));

        FoodCrisis crisis = new FoodCrisis();
        crisis.setStatus(FoodCrisis.CrisisStatus.ACTIVE);

        CrisisAffectedProduct association = new CrisisAffectedProduct();
        association.setProduct(product);
        association.setFoodCrisis(crisis);
        association.setOriginalAvailabilityPercentage(new BigDecimal("85.00"));

        product.setAvailabilityPercentage(BigDecimal.ZERO); // Current state is blocked

        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(crisisAffectedProductRepository.findByProductInAndFoodCrisisStatus(anyList(), any()))
                .thenReturn(List.of(association));

        // Act
        traceabilityService.liftCrisis(request);

        // Assert
        assertEquals(new BigDecimal("85.00"), product.getAvailabilityPercentage());
        assertEquals(FoodCrisis.CrisisStatus.LIFTED, crisis.getStatus());
        verify(productRepository).saveAll(anyList());
        verify(foodCrisisRepository).saveAll(anySet());
    }
}
