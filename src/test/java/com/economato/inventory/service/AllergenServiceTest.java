package com.economato.inventory.service;

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

import com.economato.inventory.dto.projection.AllergenProjection;
import com.economato.inventory.dto.request.AllergenRequestDTO;
import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.mapper.AllergenMapper;
import com.economato.inventory.model.Allergen;
import com.economato.inventory.repository.AllergenRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllergenServiceTest {

    @Mock
    private AllergenRepository repository;

    @Mock
    private AllergenMapper allergenMapper;

    @InjectMocks
    private AllergenService allergenService;

    private Allergen testAllergen;
    private AllergenRequestDTO testAllergenRequestDTO;
    private AllergenResponseDTO testAllergenResponseDTO;
    private AllergenProjection testProjection;

    @BeforeEach
    void setUp() {
        testAllergen = new Allergen();
        testAllergen.setId(1);
        testAllergen.setName("Test Allergen");

        testAllergenRequestDTO = new AllergenRequestDTO();
        testAllergenRequestDTO.setName("Test Allergen");

        testAllergenResponseDTO = new AllergenResponseDTO();
        testAllergenResponseDTO.setId(1);
        testAllergenResponseDTO.setName("Test Allergen");

        testProjection = mock(AllergenProjection.class);
        lenient().when(testProjection.getId()).thenReturn(1);
        lenient().when(testProjection.getName()).thenReturn("Test Allergen");

        lenient().when(allergenMapper.toResponseDTO(any(AllergenProjection.class))).thenReturn(testAllergenResponseDTO);
    }

    @Test
    void findAll_ShouldReturnPageOfAllergens() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<AllergenProjection> page = new PageImpl<>(Arrays.asList(testProjection));

        when(repository.findAllProjectedBy(pageable)).thenReturn(page);

        Page<AllergenResponseDTO> result = allergenService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(repository).findAllProjectedBy(pageable);
    }

    @Test
    void findById_WhenAllergenExists_ShouldReturnAllergen() {

        when(repository.findProjectedById(1)).thenReturn(Optional.of(testProjection));

        Optional<AllergenResponseDTO> result = allergenService.findById(1);

        assertTrue(result.isPresent());
        assertEquals(testAllergenResponseDTO.getName(), result.get().getName());
        verify(repository).findProjectedById(1);
    }

    @Test
    void findById_WhenAllergenDoesNotExist_ShouldReturnEmpty() {

        when(repository.findProjectedById(999)).thenReturn(Optional.empty());

        Optional<AllergenResponseDTO> result = allergenService.findById(999);

        assertFalse(result.isPresent());
        verify(repository).findProjectedById(999);
    }

    @Test
    void save_ShouldCreateAllergen() {

        when(allergenMapper.toEntity(testAllergenRequestDTO)).thenReturn(testAllergen);
        when(repository.save(testAllergen)).thenReturn(testAllergen);
        when(allergenMapper.toResponseDTO(testAllergen)).thenReturn(testAllergenResponseDTO);

        AllergenResponseDTO result = allergenService.save(testAllergenRequestDTO);

        assertNotNull(result);
        assertEquals(testAllergenResponseDTO.getName(), result.getName());
        verify(repository).save(testAllergen);
    }

    @Test
    void update_WhenAllergenExists_ShouldUpdateAllergen() {

        when(repository.findById(1)).thenReturn(Optional.of(testAllergen));
        when(repository.save(testAllergen)).thenReturn(testAllergen);
        when(allergenMapper.toResponseDTO(testAllergen)).thenReturn(testAllergenResponseDTO);
        doNothing().when(allergenMapper).updateEntity(testAllergenRequestDTO, testAllergen);

        Optional<AllergenResponseDTO> result = allergenService.update(1, testAllergenRequestDTO);

        assertTrue(result.isPresent());
        verify(repository).findById(1);
        verify(allergenMapper).updateEntity(testAllergenRequestDTO, testAllergen);
        verify(repository).save(testAllergen);
    }

    @Test
    void update_WhenAllergenDoesNotExist_ShouldReturnEmpty() {

        when(repository.findById(999)).thenReturn(Optional.empty());

        Optional<AllergenResponseDTO> result = allergenService.update(999, testAllergenRequestDTO);

        assertFalse(result.isPresent());
        verify(repository).findById(999);
        verify(repository, never()).save(any(Allergen.class));
    }

    @Test
    void deleteById_ShouldCallRepository() {

        when(repository.existsById(1)).thenReturn(true);
        doNothing().when(repository).deleteById(1);

        allergenService.deleteById(1);

        verify(repository).deleteById(1);
    }

    @Test
    void findByName_WhenAllergenExists_ShouldReturnFirst() {

        List<AllergenProjection> allergens = Arrays.asList(testProjection);
        when(repository.findProjectedByNameContainingIgnoreCase("Test")).thenReturn(allergens);

        Optional<AllergenResponseDTO> result = allergenService.findByName("Test");

        assertTrue(result.isPresent());
        assertEquals(testAllergenResponseDTO.getName(), result.get().getName());
        verify(repository).findProjectedByNameContainingIgnoreCase("Test");
    }

    @Test
    void findByName_WhenNoAllergenFound_ShouldReturnEmpty() {

        when(repository.findProjectedByNameContainingIgnoreCase("NonExistent")).thenReturn(Arrays.asList());

        Optional<AllergenResponseDTO> result = allergenService.findByName("NonExistent");

        assertFalse(result.isPresent());
        verify(repository).findProjectedByNameContainingIgnoreCase("NonExistent");
    }
}
