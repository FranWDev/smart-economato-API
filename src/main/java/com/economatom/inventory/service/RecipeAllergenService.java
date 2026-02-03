package com.economatom.inventory.service;

import com.economatom.inventory.dto.response.AllergenResponseDTO;
import com.economatom.inventory.dto.response.RecipeResponseDTO;
import com.economatom.inventory.exception.InvalidOperationException;
import com.economatom.inventory.exception.ResourceNotFoundException;
import com.economatom.inventory.model.Allergen;
import com.economatom.inventory.model.Recipe;
import com.economatom.inventory.model.RecipeAllergen;
import com.economatom.inventory.repository.RecipeAllergenRepository;
import com.economatom.inventory.repository.RecipeRepository;
import com.economatom.inventory.repository.AllergenRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
public class RecipeAllergenService {
    
    private final RecipeAllergenRepository repository;
    private final RecipeService recipeService;
    private final AllergenService allergenService;
    private final RecipeRepository recipeRepository;
    private final AllergenRepository allergenRepository;

    public RecipeAllergenService(
            RecipeAllergenRepository repository,
            RecipeService recipeService,
            AllergenService allergenService,
            RecipeRepository recipeRepository,
            AllergenRepository allergenRepository) {
        this.repository = repository;
        this.recipeService = recipeService;
        this.allergenService = allergenService;
        this.recipeRepository = recipeRepository;
        this.allergenRepository = allergenRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeAllergen> findAll(Pageable pageable) {
        return repository.findAll(pageable).getContent();
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
    public RecipeAllergen save(RecipeAllergen entity) {
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<RecipeAllergen> findById(Integer id) {
        return repository.findById(id);
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
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
        return recipeService.findById(recipeId)
                .map(recipeDto -> {
                    Recipe recipe = parseRecipeDtoToEntity(recipeDto);
                    return repository.findByRecipe(recipe);
                });
    }

    @Transactional(readOnly = true)
    public Optional<List<RecipeAllergen>> getByAllergenId(Integer allergenId) {
        return allergenService.findById(allergenId)
                .map(allergenDto -> {
                    Allergen allergen = parseAllergenDtoToEntity(allergenDto);
                    return repository.findByAllergen(allergen);
                });
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
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

    @Transactional(rollbackFor = {InvalidOperationException.class, RuntimeException.class, Exception.class})
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
                .map(ra -> new AllergenResponseDTO(ra.getAllergen().getId(), ra.getAllergen().getName()))
                .toList();
        return Optional.of(allergens);
    }

    // Helper methods to parse DTOs to entities
    private Recipe parseRecipeDtoToEntity(RecipeResponseDTO dto) {
        Recipe recipe = new Recipe();
        recipe.setId(dto.getId());
        recipe.setName(dto.getName());
        recipe.setElaboration(dto.getElaboration());
        recipe.setPresentation(dto.getPresentation());
        recipe.setTotalCost(dto.getTotalCost());
        return recipe;
    }

    private Allergen parseAllergenDtoToEntity(AllergenResponseDTO dto) {
        Allergen allergen = new Allergen();
        allergen.setId(dto.getId());
        allergen.setName(dto.getName());
        return allergen;
    }
}