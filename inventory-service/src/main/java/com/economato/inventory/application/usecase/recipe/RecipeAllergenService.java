package com.economato.inventory.application.usecase.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.recipe.response.AllergenResponseDTO;
import com.economato.inventory.application.mapper.recipe.AllergenMapper;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.domain.model.recipe.Allergen;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeAllergen;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeAllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeAllergenService {

    private final RecipeAllergenRepository repository;
    private final RecipeRepository recipeRepository;
    private final AllergenRepository allergenRepository;
    private final AllergenMapper allergenMapper;
    private final I18nService i18nService;

    public RecipeAllergenService(
            RecipeAllergenRepository repository,
            RecipeRepository recipeRepository,
            AllergenRepository allergenRepository,
            AllergenMapper allergenMapper,
            I18nService i18nService) {
        this.repository = repository;
        this.recipeRepository = recipeRepository;
        this.allergenRepository = allergenRepository;
        this.allergenMapper = allergenMapper;
        this.i18nService = i18nService;
    }

    @Transactional(readOnly = true)
    public Page<RecipeAllergen> findAll(Pageable pageable) {
        Page<RecipeAllergen> page = repository.findAll(pageable);
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public RecipeAllergen save(RecipeAllergen entity) {
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<RecipeAllergen> findById(Integer id) {
        return repository.findById(id);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RecipeAllergen> findByRecipe(Recipe recipe) {
        return repository.findByRecipe(recipe);
    }

    @Transactional(readOnly = true)
    public List<RecipeAllergen> findByAllergen(Allergen allergen) {
        return repository.findByAllergen(allergen);
    }

    @Transactional(readOnly = true)
    public Optional<List<RecipeAllergen>> getByRecipeId(Integer recipeId) {
        return recipeRepository.findById(recipeId)
                .map(repository::findByRecipe);
    }

    @Transactional(readOnly = true)
    public Optional<List<RecipeAllergen>> getByAllergenId(Integer allergenId) {
        return allergenRepository.findById(allergenId)
                .map(repository::findByAllergen);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void addAllergenToRecipe(Integer recipeId, Integer allergenId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND)));
        Allergen allergen = allergenRepository.findById(allergenId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        // Verificar si la asociación ya existe
        List<RecipeAllergen> existing = repository.findByRecipeAndAllergen(recipe, allergen);
        if (existing.isEmpty()) {
            RecipeAllergen recipeAllergen = new RecipeAllergen();
            recipeAllergen.setRecipe(recipe);
            recipeAllergen.setAllergen(allergen);
            repository.save(recipeAllergen);
        }
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void removeAllergenFromRecipe(Integer recipeId, Integer allergenId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND)));
        Allergen allergen = allergenRepository.findById(allergenId)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));

        List<RecipeAllergen> associations = repository.findByRecipeAndAllergen(recipe, allergen);
        repository.deleteAll(associations);
    }

    @Transactional(readOnly = true)
    public Optional<List<AllergenResponseDTO>> getAllergensForRecipe(Integer recipeId) {
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        if (recipeOpt.isEmpty()) {
            return Optional.empty();
        }
        List<RecipeAllergen> associations = repository.findByRecipeWithAllergen(recipeOpt.get());
        List<AllergenResponseDTO> allergens = associations.stream()
                .map(ra -> allergenMapper.toResponseDTO(ra.getAllergen()))
                .toList();
        return Optional.of(allergens);
    }
}