package com.economato.inventory.application.usecase.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.recipe.response.RecipeCookingAuditResponseDTO;
import com.economato.inventory.application.mapper.recipe.RecipeCookingAuditMapper;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecipeCookingAuditService {

    private final RecipeCookingAuditRepository repository;
    private final RecipeCookingAuditMapper mapper;

    public RecipeCookingAuditService(
            RecipeCookingAuditRepository repository,
            RecipeCookingAuditMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<RecipeCookingAuditResponseDTO> findAll(Pageable pageable) {
        Page<RecipeCookingAuditResponseDTO> page = repository.findAllOrderByDateDesc(pageable)
                .map(mapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    public List<RecipeCookingAuditResponseDTO> findByRecipeId(Integer recipeId) {
        return repository.findByRecipeId(recipeId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByUserId(Integer userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByRecipeNameContainingIgnoreCase(String recipeName) {
        return repository.findByRecipeNameContainingIgnoreCase(recipeName).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByUserNameContainingIgnoreCase(String userName) {
        return repository.findByUserNameContainingIgnoreCase(userName).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return repository.findByDateRange(startDate, endDate).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
