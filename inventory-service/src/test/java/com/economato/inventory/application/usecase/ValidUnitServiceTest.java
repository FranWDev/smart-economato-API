package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.ValidUnit;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ValidUnitRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidUnitServiceTest {

    @Mock private ValidUnitRepository validUnitRepository;
    @Mock private ProductRepository productRepository;
    @Mock private I18nService i18nService;

    private ValidUnitService service;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(inv -> inv.getArgument(0, MessageKey.class).getKey());
        service = new ValidUnitService(validUnitRepository, productRepository, i18nService);
    }

    @Test
    void create_ShouldSaveNewUnitUppercased() {
        setUpSilently();
        when(validUnitRepository.existsByCodeIgnoreCase("LITRO")).thenReturn(false);
        when(validUnitRepository.save(any(ValidUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidUnit created = service.create("litro", "volumen");

        assertEquals("LITRO", created.getCode());
        assertEquals("VOLUMEN", created.getCategory());
        assertTrue(created.isActive());
        verify(validUnitRepository).save(any(ValidUnit.class));
    }

    @Test
    void create_WhenCodeExists_ShouldThrow() {
        setUpSilently();
        when(validUnitRepository.existsByCodeIgnoreCase("KG")).thenReturn(true);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.create("kg", "peso"));
        assertNotNull(exception);
        verify(validUnitRepository, never()).save(any());
    }

    @Test
    void update_WhenCodeChangesToExisting_ShouldThrow() {
        setUpSilently();
        ValidUnit existing = ValidUnit.builder().id(1).code("KG").category("PESO").active(true).build();
        when(validUnitRepository.findById(1)).thenReturn(Optional.of(existing));
        when(validUnitRepository.existsByCodeIgnoreCase("L")).thenReturn(true);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.update(1, "L", "VOLUMEN", true));
        assertNotNull(exception);
    }

    @Test
    void toggleActive_WhenUnitInUse_ShouldThrow() {
        setUpSilently();
        ValidUnit existing = ValidUnit.builder().id(1).code("KG").category("PESO").active(true).build();
        when(validUnitRepository.findById(1)).thenReturn(Optional.of(existing));
        when(productRepository.existsByUnitIgnoreCaseAndIsHiddenFalse("KG")).thenReturn(true);

        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> service.toggleActive(1));
        assertNotNull(exception);
    }

    @Test
    void toggleActive_WhenUnitNotInUse_ShouldToggle() {
        setUpSilently();
        ValidUnit existing = ValidUnit.builder().id(1).code("KG").category("PESO").active(true).build();
        when(validUnitRepository.findById(1)).thenReturn(Optional.of(existing));
        when(productRepository.existsByUnitIgnoreCaseAndIsHiddenFalse("KG")).thenReturn(false);
        when(validUnitRepository.save(any(ValidUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidUnit toggled = service.toggleActive(1);

        assertFalse(toggled.isActive());
        verify(validUnitRepository).save(any(ValidUnit.class));
    }

    @Test
    void getActive_ShouldReturnOnlyActiveUnits() {
        setUpSilently();
        ValidUnit u1 = ValidUnit.builder().id(1).code("KG").category("PESO").active(true).build();
        when(validUnitRepository.findByActiveTrueOrderByCodeAsc()).thenReturn(List.of(u1));

        List<ValidUnit> active = service.getActive();

        assertEquals(1, active.size());
        assertEquals("KG", active.get(0).getCode());
    }

    @Test
    void getAll_ShouldReturnSortedList() {
        setUpSilently();
        ValidUnit u1 = ValidUnit.builder().id(1).code("G").category("PESO").active(true).build();
        ValidUnit u2 = ValidUnit.builder().id(2).code("L").category("VOLUMEN").active(true).build();
        when(validUnitRepository.findAllByOrderByCategoryAscCodeAsc()).thenReturn(List.of(u1, u2));

        List<ValidUnit> result = service.getAll();

        assertEquals(2, result.size());
        assertEquals("G", result.get(0).getCode());
    }

    private void setUpSilently() {
        setUp();
    }
}
