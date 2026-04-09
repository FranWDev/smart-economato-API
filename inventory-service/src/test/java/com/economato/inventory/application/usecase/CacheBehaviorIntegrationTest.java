package com.economato.inventory.application.usecase;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.economato.inventory.application.dto.request.AllergenRequestDTO;
import com.economato.inventory.application.dto.request.SupplierRequestDTO;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.SupplierRepository;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.cache.type=simple")
class CacheBehaviorIntegrationTest {

    @Autowired
    private AllergenService allergenService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private AllergenRepository allergenRepository;

    @MockitoSpyBean
    private SupplierRepository supplierRepository;

    @BeforeEach
    void setUp() {
        supplierRepository.deleteAll();
        allergenRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        clearInvocations(allergenRepository);
        clearInvocations(supplierRepository);
    }

    @Test
    void findById_afterSave_usesCacheHit() {
        var saved = allergenService.save(new AllergenRequestDTO("Gluten"));

        allergenService.findById(saved.getId());
        allergenService.findById(saved.getId());

        verify(allergenRepository, times(1)).findProjectedById(saved.getId());
    }

    @Test
    void update_invalidatesOnlySpecificEntry() {
        var a1 = allergenService.save(new AllergenRequestDTO("Lactosa"));
        var a2 = allergenService.save(new AllergenRequestDTO("Huevo"));

        allergenService.findById(a1.getId());
        allergenService.findById(a2.getId());

        clearInvocations(allergenRepository);

        allergenService.update(a1.getId(), new AllergenRequestDTO("Lactosa Premium"));

        clearInvocations(allergenRepository);

        allergenService.findById(a1.getId());
        allergenService.findById(a2.getId());

        verify(allergenRepository, times(1)).findProjectedById(a1.getId());
        verify(allergenRepository, never()).findProjectedById(a2.getId());
    }

    @Test
    void masterData_pages_areCached() {
        supplierService.save(new SupplierRequestDTO("Proveedor A", "a@test.com", "600000001"));
        supplierService.save(new SupplierRequestDTO("Proveedor B", "b@test.com", "600000002"));

        supplierService.findAll(PageRequest.of(0, 10));
        supplierService.findAll(PageRequest.of(0, 10));

        verify(supplierRepository, times(1)).findAllProjectedBy(PageRequest.of(0, 10));
    }
}
