package com.economato.inventory.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.model.Allergen;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeAllergen;
import com.economato.inventory.repository.AllergenRepository;
import com.economato.inventory.repository.RecipeAllergenRepository;
import com.economato.inventory.repository.RecipeRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeAllergenService {

    private final RecipeAllergenRepository repository;
    private final RecipeRepository recipeRepository;
    private final AllergenRepository allergenRepository;
    private final com.economato.inventory.mapper.AllergenMapper allergenMapper;

    public RecipeAllergenService(
            RecipeAllergenRepository repository,
            RecipeRepository recipeRepository,
            AllergenRepository allergenRepository,
            com.economato.inventory.mapper.AllergenMapper allergenMapper) {
        this.repository = repository;
        this.recipeRepository = recipeRepository;
        this.allergenRepository = allergenRepository;
        this.allergenMapper = allergenMapper;
    }

    @Transactional(readOnly = true)
    public List<RecipeAllergen> findAll(Pageable pageable) {
        return repository.findAll(pageable).getContent();
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
    public boolean addAllergenToRecipe(Integer recipeId, Integer allergenId) {
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        Optional<Allergen> allergenOpt = allergenRepository.findById(allergenId);

        if (recipeOpt.isEmpty() || allergenOpt.isEmpty()) {
            return false;
        }

        Recipe recipe = recipeOpt.get();
        Allergen allergen = allergenOpt.get();

        // Verificar si la asociación ya existe
        List<RecipeAllergen> existing = repository.findByRecipeAndAllergen(recipe, allergen);
        if (!existing.isEmpty()) {
            return true; // Ya existe, no hacer nada
        }

        RecipeAllergen recipeAllergen = new RecipeAllergen();
        recipeAllergen.setRecipe(recipe);
        recipeAllergen.setAllergen(allergen);

        repository.save(recipeAllergen);
        return true;
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public boolean removeAllergenFromRecipe(Integer recipeId, Integer allergenId) {
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        Optional<Allergen> allergenOpt = allergenRepository.findById(allergenId);

        if (recipeOpt.isEmpty() || allergenOpt.isEmpty()) {
            return false;
        }

        Recipe recipe = recipeOpt.get();
        Allergen allergen = allergenOpt.get();

        List<RecipeAllergen> associations = repository.findByRecipeAndAllergen(recipe, allergen);
        repository.deleteAll(associations);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<List<AllergenResponseDTO>> getAllergensForRecipe(Integer recipeId) {
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        if (recipeOpt.isEmpty()) {
            return Optional.empty();
        }
        List<RecipeAllergen> associations = repository.findByRecipe(recipeOpt.get());
        List<AllergenResponseDTO> allergens = associations.stream()
                .map(ra -> allergenMapper.toResponseDTO(ra.getAllergen()))
                .toList();
        return Optional.of(allergens);
    }
}