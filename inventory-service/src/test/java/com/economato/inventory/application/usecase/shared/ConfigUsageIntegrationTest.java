package com.economato.inventory.application.usecase.shared;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.notification.PersistentNotificationService;
import com.economato.inventory.application.usecase.notification.UserPresenceService;
import com.economato.inventory.application.usecase.notification.WebSocketNotificationService;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.product.ProductService;
import com.economato.inventory.application.usecase.product.ValidUnitService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.stock.ScheduledForecastRefreshService;
import com.economato.inventory.infrastructure.config.shared.PredictionConfig;
import com.economato.inventory.application.dto.product.response.ProductResponseDTO;
import com.economato.inventory.application.dto.user.presence.UserSessionInfo;

import com.economato.inventory.application.dto.product.request.ProductRequestDTO;
import com.economato.inventory.application.mapper.product.ProductMapper;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.shared.InventoryAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeComponentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ValidUnit;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ConfigUsageIntegrationTest {

    @Mock private SystemConfigService systemConfigService;
    @Mock private AuditEventProducer auditEventProducer;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ProductRepository productRepository;
    @Mock private StockLedgerRepository stockLedgerRepository;
    @Mock private StockLedgerService stockLedgerService;
    @Mock private WebSocketNotificationService webSocketNotificationService;
    @Mock private PersistentNotificationService persistentNotificationService;
    @Mock private I18nService i18nService;
    @Mock private ValidUnitService validUnitService;
    @Mock private RecipeService recipeService;

    private void init() {
        lenient().when(systemConfigService.isPresenceAuditEnabled()).thenReturn(true);
        lenient().when(systemConfigService.getStaleSessionTimeoutSeconds()).thenReturn(60L);
        lenient().when(systemConfigService.isNotificationTypeEnabled(any())).thenReturn(true);
        lenient().when(systemConfigService.getPredictionConfig()).thenReturn(new SystemConfigService.PredictionConfig(true, 12, 30, 7));
        lenient().when(systemConfigService.getOutboxConfig()).thenReturn(new SystemConfigService.OutboxConfig(5000L, 50, 3, 5));
        lenient().when(systemConfigService.getMaxUploadFileSizeBytes()).thenReturn(1024L * 1024L);
        lenient().when(systemConfigService.getAllowedFileTypes()).thenReturn(java.util.Set.of("image/jpeg"));
        lenient().when(systemConfigService.getMaxChatMessageLength()).thenReturn(100);
        lenient().when(validUnitService.getActive()).thenReturn(List.of(ValidUnit.builder().id(1).code("LITRO").category("VOLUMEN").active(true).build()));
        lenient().when(i18nService.getMessage(any())).thenAnswer(inv -> inv.getArgument(0).toString());
    }

    @Test
    void userPresence_WhenAuditDisabled_ShouldSkipPublishing() throws Exception {
        init();
        when(systemConfigService.isPresenceAuditEnabled()).thenReturn(false);
        when(userRepository.findByName("juan")).thenReturn(Optional.of(user(1, "Juan", "juan", Role.USER)));

        UserPresenceService service = new UserPresenceService(messagingTemplate, userRepository);
        setField(service, "systemConfigService", systemConfigService);
        setField(service, "auditEventProducer", auditEventProducer);

        service.userConnected("juan", "s1", null, null, null, null);

        verify(auditEventProducer, never()).publishPresenceAudit(any());
    }

    @Test
    void userPresence_ShouldUseConfiguredStaleTimeout() throws Exception {
        init();
        lenient().when(systemConfigService.getStaleSessionTimeoutSeconds()).thenReturn(5L);
        when(userRepository.findByName("juan")).thenReturn(Optional.of(user(1, "Juan", "juan", Role.USER)));

        UserPresenceService service = new UserPresenceService(messagingTemplate, userRepository);
        setField(service, "systemConfigService", systemConfigService);
        setField(service, "auditEventProducer", auditEventProducer);

        service.userConnected("juan", "s1", null, null, null, null);
        setLastActivity(service, "juan", "s1", LocalDateTime.now().minusSeconds(10));
        service.cleanupStaleSessions();

        assertTrue(service.getConnectedUsers().isEmpty());
        verify(auditEventProducer, Mockito.times(2)).publishPresenceAudit(any());
    }

    @Test
    void scheduledForecastRefresh_ShouldUseConfiguredBatchSizeAndHistory() throws Exception {
        init();
        Product p1 = new Product(); p1.setId(1); p1.setHidden(false); p1.setName("P1");
        Product p2 = new Product(); p2.setId(2); p2.setHidden(false); p2.setName("P2");
        Product p3 = new Product(); p3.setId(3); p3.setHidden(false); p3.setName("P3");
        Product p4 = new Product(); p4.setId(4); p4.setHidden(false); p4.setName("P4");
        Product p5 = new Product(); p5.setId(5); p5.setHidden(false); p5.setName("P5");
        Product p6 = new Product(); p6.setId(6); p6.setHidden(false); p6.setName("P6");
        Product p7 = new Product(); p7.setId(7); p7.setHidden(false); p7.setName("P7");
        Product p8 = new Product(); p8.setId(8); p8.setHidden(false); p8.setName("P8");
        Product p9 = new Product(); p9.setId(9); p9.setHidden(false); p9.setName("P9");
        Product p10 = new Product(); p10.setId(10); p10.setHidden(false); p10.setName("P10");
        Product p11 = new Product(); p11.setId(11); p11.setHidden(false); p11.setName("P11");
        Product p12 = new Product(); p12.setId(12); p12.setHidden(false); p12.setName("P12");
        Product p13 = new Product(); p13.setId(13); p13.setHidden(false); p13.setName("P13");
        Product p14 = new Product(); p14.setId(14); p14.setHidden(false); p14.setName("P14");
        Product p15 = new Product(); p15.setId(15); p15.setHidden(false); p15.setName("P15");
        Product p16 = new Product(); p16.setId(16); p16.setHidden(false); p16.setName("P16");
        Product p17 = new Product(); p17.setId(17); p17.setHidden(false); p17.setName("P17");
        Product p18 = new Product(); p18.setId(18); p18.setHidden(false); p18.setName("P18");
        Product p19 = new Product(); p19.setId(19); p19.setHidden(false); p19.setName("P19");
        Product p20 = new Product(); p20.setId(20); p20.setHidden(false); p20.setName("P20");
        Product p21 = new Product(); p21.setId(21); p21.setHidden(false); p21.setName("P21");
        Product p22 = new Product(); p22.setId(22); p22.setHidden(false); p22.setName("P22");
        Product p23 = new Product(); p23.setId(23); p23.setHidden(false); p23.setName("P23");
        Product p24 = new Product(); p24.setId(24); p24.setHidden(false); p24.setName("P24");
        Product p25 = new Product(); p25.setId(25); p25.setHidden(false); p25.setName("P25");

        when(stockLedgerRepository.findProductIdsWithMovementsSince(any())).thenReturn(List.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25));
        when(productRepository.findAllActive()).thenReturn(List.of(p1,p2,p3,p4,p5,p6,p7,p8,p9,p10,p11,p12,p13,p14,p15,p16,p17,p18,p19,p20,p21,p22,p23,p24,p25));
        when(stockLedgerService.getDailyConsumptionBatch(any(), any(), any())).thenReturn(java.util.Collections.emptyMap());

        AuditEventProducer outboxProducer = mock(AuditEventProducer.class);

        ScheduledForecastRefreshService service = new ScheduledForecastRefreshService(
                productRepository,
                stockLedgerRepository,
                stockLedgerService,
            outboxProducer,
                webSocketNotificationService,
                persistentNotificationService
        );
        setField(service, "systemConfigService", systemConfigService);

        service.scheduleForecastRefresh();

        verify(outboxProducer, Mockito.times(4)).publishStockPredictionEvent(any());
        verify(webSocketNotificationService).notifyAdminsStockPrediction(25);
        verify(persistentNotificationService).notifyStockPrediction(25);
    }

    @Test
    void fileStorage_ShouldUseConfigOverrides() throws Exception {
        init();
        Path tempDir = java.nio.file.Files.createTempDirectory("cfg-upload-test");
        FileStorageService service = new FileStorageService(tempDir.toString(), 1, "application/x-msdownload", i18nService);
        setField(service, "systemConfigService", systemConfigService);

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "content".getBytes(StandardCharsets.UTF_8));
        String relativePath = service.store(1L, 1L, file);

        assertNotNull(relativePath);
        assertTrue(relativePath.contains("incidents/1/1_a.jpg"));
    }

    @Test
    void productService_ShouldAcceptConfiguredUnit() throws Exception {
        init();
        ProductRepository repository = mock(ProductRepository.class);
        InventoryAuditRepository movementRepository = mock(InventoryAuditRepository.class);
        RecipeComponentRepository recipeComponentRepository = mock(RecipeComponentRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        StockLedgerService productStockLedgerService = mock(StockLedgerService.class);
        ProductBatchService productBatchService = mock(ProductBatchService.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        I18nService localI18n = i18nService;

        ProductService service = new ProductService(
                localI18n,
                repository,
                movementRepository,
                recipeComponentRepository,
                supplierRepository,
                productMapper,
                productStockLedgerService,
                productBatchService,
                securityContextHelper,
                recipeService
        );
        setField(service, "validUnitService", validUnitService);

        User current = new User();
        current.setId(10);
        current.setName("Admin");
        current.setRole(Role.ADMIN);
        lenient().when(securityContextHelper.getCurrentUser()).thenReturn(current);

        ProductRequestDTO request = new ProductRequestDTO();
        request.setName("Agua");
        request.setUnit("LITRO");
        request.setUnitPrice(BigDecimal.ONE);
        request.setProductCode("AGUA-1");
        request.setCurrentStock(BigDecimal.ZERO);
        request.setSupplierId(null);

        Product entity = new Product();
        entity.setId(1);
        entity.setName("Agua");
        entity.setUnit("LITRO");
        entity.setCurrentStock(BigDecimal.ZERO);
        entity.setProductCode("AGUA-1");

        lenient().when(repository.existsByName("Agua")).thenReturn(false);
        lenient().when(repository.existsByProductCode("AGUA-1")).thenReturn(false);
        lenient().when(productMapper.toEntity(request)).thenReturn(entity);
        lenient().when(repository.saveAndFlush(any(Product.class))).thenReturn(entity);
        lenient().when(productMapper.toResponseDTO(entity)).thenReturn(new ProductResponseDTO());

        var result = service.save(request);

        assertNotNull(result);
        verify(validUnitService).getActive();
    }

    private static User user(int id, String name, String username, Role role) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setUser(username);
        u.setRole(role);
        return u;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static void setLastActivity(UserPresenceService service, String username, String sessionId, LocalDateTime time) throws Exception {
        Field field = UserPresenceService.class.getDeclaredField("sessionsByUser");
        field.setAccessible(true);
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, UserSessionInfo>> map =
                (java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, UserSessionInfo>>) field.get(service);
        map.get(username).get(sessionId).setLastActivityAt(time);
    }
}