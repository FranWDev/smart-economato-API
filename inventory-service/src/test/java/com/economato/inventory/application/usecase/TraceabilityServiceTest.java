package com.economato.inventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.economato.inventory.application.dto.request.CrisisActivationRequestDTO;
import com.economato.inventory.application.dto.request.CrisisLiftRequestDTO;
import com.economato.inventory.application.dto.response.CrisisResponseDTO;
import com.economato.inventory.application.mapper.OrderMapper;
import com.economato.inventory.application.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.application.mapper.StockLedgerMapper;
import com.economato.inventory.domain.model.CrisisAffectedProduct;
import com.economato.inventory.domain.model.FoodCrisis;
import com.economato.inventory.domain.model.Product;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
    }

    @Test
    void activateCrisis_ShouldSavePersistentState() {
        CrisisActivationRequestDTO request = new CrisisActivationRequestDTO();
        request.setSupplierId(1);
        request.setProductIds(List.of(1));
        request.setReason("Contamination");
        request.setDateFrom(LocalDateTime.now().minusDays(1));
        request.setDateTo(LocalDateTime.now());

        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(productRepository.findByIdsForUpdate(List.of(1))).thenReturn(List.of(product));
        when(securityContextHelper.getCurrentUser()).thenReturn(admin);
        when(foodCrisisRepository.save(any(FoodCrisis.class))).thenAnswer(i -> {
            FoodCrisis crisis = i.getArgument(0);
            crisis.setId(99L);
            return crisis;
        });
        when(ledgerService.recordBatchStockMovements(anyList(), any(), any())).thenReturn(List.of());
        when(crisisAffectedProductRepository.findByFoodCrisisId(99L)).thenReturn(List.of());

        CrisisResponseDTO response = traceabilityService.activateCrisis(request);

        assertNotNull(response);
        assertEquals(99L, response.getCrisisId());
        verify(productRepository).updateAvailabilityForProducts(eq(List.of(1)), eq(BigDecimal.ZERO));
        verify(foodCrisisRepository).save(any(FoodCrisis.class));
        verify(crisisAffectedProductRepository).saveAll(anyList());
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
}
