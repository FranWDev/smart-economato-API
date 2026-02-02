package com.economatom.inventory.service;

import com.economatom.inventory.dto.request.SupplierRequestDTO;
import com.economatom.inventory.dto.response.SupplierResponseDTO;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.mapper.SupplierMapper;
import com.economatom.inventory.model.Supplier;
import com.economatom.inventory.repository.SupplierRepository;
import com.economatom.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository repository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SupplierMapper supplierMapper;

    @InjectMocks
    private SupplierService supplierService;

    private Supplier testSupplier;
    private SupplierRequestDTO testSupplierRequestDTO;
    private SupplierResponseDTO testSupplierResponseDTO;

    @BeforeEach
    void setUp() {
        testSupplier = new Supplier();
        testSupplier.setId(1);
        testSupplier.setName("Test Supplier");

        testSupplierRequestDTO = new SupplierRequestDTO();
        testSupplierRequestDTO.setName("Test Supplier");

        testSupplierResponseDTO = new SupplierResponseDTO();
        testSupplierResponseDTO.setId(1);
        testSupplierResponseDTO.setName("Test Supplier");
    }

    @Test
    void findAll_ShouldReturnPageOfSuppliers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Supplier> page = new PageImpl<>(Arrays.asList(testSupplier));

        when(repository.findAll(pageable)).thenReturn(page);
        when(supplierMapper.toResponseDTO(testSupplier)).thenReturn(testSupplierResponseDTO);

        Page<SupplierResponseDTO> result = supplierService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(repository).findAll(pageable);
    }

    @Test
    void findById_WhenSupplierExists_ShouldReturnSupplier() {
        when(repository.findById(1)).thenReturn(Optional.of(testSupplier));
        when(supplierMapper.toResponseDTO(testSupplier)).thenReturn(testSupplierResponseDTO);

        Optional<SupplierResponseDTO> result = supplierService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testSupplierResponseDTO.getName(), result.get().getName());
        verify(repository).findById(1);
    }

    @Test
    void findById_WhenSupplierDoesNotExist_ShouldReturnEmpty() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<SupplierResponseDTO> result = supplierService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
    }

    @Test
    void save_WhenNameIsUnique_ShouldCreateSupplier() {
        when(repository.existsByName(testSupplierRequestDTO.getName())).thenReturn(false);
        when(supplierMapper.toEntity(testSupplierRequestDTO)).thenReturn(testSupplier);
        when(repository.save(testSupplier)).thenReturn(testSupplier);
        when(supplierMapper.toResponseDTO(testSupplier)).thenReturn(testSupplierResponseDTO);

        SupplierResponseDTO result = supplierService.save(testSupplierRequestDTO);

        assertNotNull(result);
        assertEquals(testSupplierResponseDTO.getName(), result.getName());
        verify(repository).existsByName(testSupplierRequestDTO.getName());
        verify(repository).save(testSupplier);
    }

    @Test
    void save_WhenNameAlreadyExists_ShouldThrowException() {
        when(repository.existsByName(testSupplierRequestDTO.getName())).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            supplierService.save(testSupplierRequestDTO);
        });

        verify(repository).existsByName(testSupplierRequestDTO.getName());
        verify(repository, never()).save(any(Supplier.class));
    }

    @Test
    void update_WhenSupplierExists_ShouldUpdateSupplier() {
        when(repository.findById(1)).thenReturn(Optional.of(testSupplier));
        when(repository.save(testSupplier)).thenReturn(testSupplier);
        when(supplierMapper.toResponseDTO(testSupplier)).thenReturn(testSupplierResponseDTO);
        doNothing().when(supplierMapper).updateEntity(testSupplierRequestDTO, testSupplier);

        Optional<SupplierResponseDTO> result = supplierService.update(1, testSupplierRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(supplierMapper).updateEntity(testSupplierRequestDTO, testSupplier);
        verify(repository).save(testSupplier);
    }

    @Test
    void update_WhenSupplierDoesNotExist_ShouldReturnEmpty() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<SupplierResponseDTO> result = supplierService.update(999, testSupplierRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Supplier.class));
    }

    @Test
    void update_WhenNewNameAlreadyExists_ShouldThrowException() {
        Supplier existingSupplier = new Supplier();
        existingSupplier.setId(1);
        existingSupplier.setName("Old Name");

        SupplierRequestDTO updateRequest = new SupplierRequestDTO();
        updateRequest.setName("New Name");

        when(repository.findById(1)).thenReturn(Optional.of(existingSupplier));
        when(repository.existsByName("New Name")).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            supplierService.update(1, updateRequest);
        });

        verify(repository).findById(1);
        verify(repository).existsByName("New Name");
        verify(repository, never()).save(any(Supplier.class));
    }

    @Test
    void deleteById_WhenNoProductsAssociated_ShouldDeleteSupplier() {
        when(repository.findById(1)).thenReturn(Optional.of(testSupplier));
        when(productRepository.existsBySupplierId(1)).thenReturn(false);
        doNothing().when(repository).deleteById(1);

        supplierService.deleteById(1);

        verify(repository).findById(1);
        verify(productRepository).existsBySupplierId(1);
        verify(repository).deleteById(1);
    }

    @Test
    void deleteById_WhenProductsAssociated_ShouldThrowException() {
        when(repository.findById(1)).thenReturn(Optional.of(testSupplier));
        when(productRepository.existsBySupplierId(1)).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> {
            supplierService.deleteById(1);
        });

        verify(repository).findById(1);
        verify(productRepository).existsBySupplierId(1);
        verify(repository, never()).deleteById(1);
    }

    @Test
    void findByNameContaining_WhenSuppliersExist_ShouldReturnList() {
        List<Supplier> suppliers = Arrays.asList(testSupplier);
        when(repository.findByNameContainingIgnoreCase("Test")).thenReturn(suppliers);
        when(supplierMapper.toResponseDTO(testSupplier)).thenReturn(testSupplierResponseDTO);

        List<SupplierResponseDTO> result = supplierService.findByNameContaining("Test");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testSupplierResponseDTO.getName(), result.get(0).getName());
        verify(repository).findByNameContainingIgnoreCase("Test");
    }

    @Test
    void findByNameContaining_WhenNoSuppliersFound_ShouldReturnEmptyList() {
        when(repository.findByNameContainingIgnoreCase("NonExistent")).thenReturn(Arrays.asList());

        List<SupplierResponseDTO> result = supplierService.findByNameContaining("NonExistent");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByNameContainingIgnoreCase("NonExistent");
    }
}
