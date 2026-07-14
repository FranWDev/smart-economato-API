package com.economato.inventory.application.usecase.recipe;

import com.economato.inventory.application.dto.recipe.response.RecipeAuditResponseDTO;
import com.economato.inventory.application.mapper.recipe.RecipeAuditMapper;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeAudit;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeAuditRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<RecipeAuditResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable)
                .map(recipeAuditMapper::toResponseDTO);
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
        return repository.findProjectedByUserId(id).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecipeAuditResponseDTO> findByMovementDateBetween(LocalDateTime start,
            LocalDateTime end) {
        return repository.findProjectedByAuditDateBetween(start, end).stream()
                .map(recipeAuditMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

}
