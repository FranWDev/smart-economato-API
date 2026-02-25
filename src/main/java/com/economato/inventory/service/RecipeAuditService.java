package com.economato.inventory.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.response.RecipeAuditResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.RecipeAuditMapper;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeAudit;
import com.economato.inventory.repository.RecipeAuditRepository;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeAuditService {

    private final RecipeAuditRepository repository;
    private final RecipeAuditMapper recipeAuditMapper;

    public RecipeAuditService(RecipeAuditRepository repository, RecipeAuditMapper recipeAuditMapper) {
        this.repository = repository;
        this.recipeAuditMapper = recipeAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<RecipeAuditResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RecipeAuditResponseDTO> findById(Integer id) {
        return repository.findById(id)
                .map(recipeAuditMapper::toResponseDTO);
    }

    @Async
    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void logRecipeAction(Recipe recipe, String action, String details) {
        RecipeAudit audit = new RecipeAudit();
        audit.setRecipe(recipe);
        audit.setAction(action);
        audit.setDetails(details);
        repository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<RecipeAuditResponseDTO> findByRecipeId(Integer id) {
        return repository.findProjectedByRecipeId(id).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecipeAuditResponseDTO> findByUserId(Integer id) {
        return repository.findProjectedByUsersId(id).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecipeAuditResponseDTO> findByMovementDateBetween(java.time.LocalDateTime start,
            java.time.LocalDateTime end) {
        return repository.findProjectedByAuditDateBetween(start, end).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

}
