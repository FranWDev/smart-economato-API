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

import com.economato.inventory.dto.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.mapper.RecipeCookingAuditMapper;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeCookingAudit;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.RecipeCookingAuditRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeCookingAuditServiceTest {

    @Mock
    private RecipeCookingAuditRepository repository;

    @Mock
    private RecipeCookingAuditMapper mapper;

    @InjectMocks
    private RecipeCookingAuditService service;

    private RecipeCookingAudit testAudit;
    private RecipeCookingAuditResponseDTO testAuditDTO;
    private Recipe testRecipe;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("Test Chef");

        testRecipe = new Recipe();
        testRecipe.setId(1);
        testRecipe.setName("Test Recipe");

        testAudit = new RecipeCookingAudit();
        testAudit.setId(1L);
        testAudit.setRecipe(testRecipe);
        testAudit.setUser(testUser);
        testAudit.setQuantityCooked(new BigDecimal("2.5"));
        testAudit.setDetails("Test cooking");
        testAudit.setCookingDate(LocalDateTime.now());

        testAuditDTO = new RecipeCookingAuditResponseDTO();
        testAuditDTO.setId(1L);
        testAuditDTO.setRecipeId(1);
        testAuditDTO.setRecipeName("Test Recipe");
        testAuditDTO.setUserId(1);
        testAuditDTO.setUserName("Test Chef");
        testAuditDTO.setQuantityCooked(new BigDecimal("2.5"));
        testAuditDTO.setDetails("Test cooking");
        testAuditDTO.setCookingDate(LocalDateTime.now());
    }

    @Test
    void findAll_ShouldReturnListOfAudits() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<RecipeCookingAudit> page = new PageImpl<>(Arrays.asList(testAudit));
        when(repository.findAllOrderByDateDesc(pageable)).thenReturn(page);
        when(mapper.toResponseDTO(testAudit)).thenReturn(testAuditDTO);

        List<RecipeCookingAuditResponseDTO> result = service.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testAuditDTO.getRecipeName(), result.get(0).getRecipeName());
        verify(repository).findAllOrderByDateDesc(pageable);
        verify(mapper).toResponseDTO(testAudit);
    }

    @Test
    void findByRecipeId_ShouldReturnAuditsForRecipe() {

        when(repository.findByRecipeId(1)).thenReturn(Arrays.asList(testAudit));
        when(mapper.toResponseDTO(testAudit)).thenReturn(testAuditDTO);

        List<RecipeCookingAuditResponseDTO> result = service.findByRecipeId(1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getRecipeId());
        verify(repository).findByRecipeId(1);
    }

    @Test
    void findByUserId_ShouldReturnAuditsForUser() {

        when(repository.findByUserId(1)).thenReturn(Arrays.asList(testAudit));
        when(mapper.toResponseDTO(testAudit)).thenReturn(testAuditDTO);

        List<RecipeCookingAuditResponseDTO> result = service.findByUserId(1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getUserId());
        assertEquals("Test Chef", result.get(0).getUserName());
        verify(repository).findByUserId(1);
    }

    @Test
    void findByDateRange_ShouldReturnAuditsInRange() {

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();

        when(repository.findByDateRange(startDate, endDate)).thenReturn(Arrays.asList(testAudit));
        when(mapper.toResponseDTO(testAudit)).thenReturn(testAuditDTO);

        List<RecipeCookingAuditResponseDTO> result = service.findByDateRange(startDate, endDate);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).findByDateRange(startDate, endDate);
    }

    @Test
    void findByRecipeId_WhenNoAuditsExist_ShouldReturnEmptyList() {

        when(repository.findByRecipeId(999)).thenReturn(Arrays.asList());

        List<RecipeCookingAuditResponseDTO> result = service.findByRecipeId(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByRecipeId(999);
    }

    @Test
    void findByUserId_WithMultipleAudits_ShouldReturnAll() {

        RecipeCookingAudit audit2 = new RecipeCookingAudit();
        audit2.setId(2L);
        audit2.setRecipe(testRecipe);
        audit2.setUser(testUser);
        audit2.setQuantityCooked(new BigDecimal("3.0"));
        audit2.setCookingDate(LocalDateTime.now());

        RecipeCookingAuditResponseDTO auditDTO2 = new RecipeCookingAuditResponseDTO();
        auditDTO2.setId(2L);
        auditDTO2.setRecipeId(1);
        auditDTO2.setUserId(1);
        auditDTO2.setQuantityCooked(new BigDecimal("3.0"));

        when(repository.findByUserId(1)).thenReturn(Arrays.asList(testAudit, audit2));
        when(mapper.toResponseDTO(testAudit)).thenReturn(testAuditDTO);
        when(mapper.toResponseDTO(audit2)).thenReturn(auditDTO2);

        List<RecipeCookingAuditResponseDTO> result = service.findByUserId(1);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(repository).findByUserId(1);
        verify(mapper, times(2)).toResponseDTO(any(RecipeCookingAudit.class));
    }
}
