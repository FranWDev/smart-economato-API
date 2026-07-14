package com.economato.inventory.application.usecase.recipe;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeComponentResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.mapper.recipe.RecipeComponentMapper;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeComponentRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeComponentService {
    private final I18nService i18nService;

    private final RecipeComponentRepository repository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeComponentMapper recipeComponentMapper;

    public RecipeComponentService(I18nService i18nService,
            RecipeComponentRepository repository,
            ProductRepository productRepository,
            RecipeRepository recipeRepository,
            RecipeComponentMapper recipeComponentMapper) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
        this.recipeComponentMapper = recipeComponentMapper;
    }

        @Cacheable(value = "recipe_components_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
        @Transactional(readOnly = true)
    public Page<RecipeComponentResponseDTO> findAll(Pageable pageable) {
        Page<RecipeComponentResponseDTO> page = repository.findAllProjectedBy(pageable)
                .map(recipeComponentMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(), page.getTotalElements());
    }

        @Cacheable(value = "recipe_component", key = "#id", unless = "#result == null")
        @Transactional(readOnly = true)
    public Optional<RecipeComponentResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(recipeComponentMapper::toResponseDTO);
    }

        @CacheEvict(value = { "recipe_components_page", "recipe_component", "recipe_components_by_recipe" }, allEntries = true)
    @RealtimeSync(entityType = "recipe", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"recipe", "weekly_plan"})
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeComponentResponseDTO save(RecipeComponentRequestDTO requestDTO) {
        RecipeComponent component = new RecipeComponent();
        updateEntity(component, requestDTO);
        repository.save(component);

        // recargar con proyección
        return repository.findProjectedById(component.getId())
                .map(recipeComponentMapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
    }

        @CacheEvict(value = { "recipe_components_page", "recipe_component", "recipe_components_by_recipe" }, allEntries = true)
    @RealtimeSync(entityType = "recipe", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"recipe", "weekly_plan"})
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<RecipeComponentResponseDTO> update(Integer id, RecipeComponentRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    repository.save(existing);

                    return repository.findProjectedById(existing.getId())
                            .map(recipeComponentMapper::toResponseDTO)
                            .orElseThrow(() -> new RuntimeException(
                                    i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND)));
                });
    }

    @CacheEvict(value = { "recipe_components_page", "recipe_component", "recipe_components_by_recipe" }, allEntries = true)
    @RealtimeSync(entityType = "recipe", action = "UPDATE", idFromArg = -2,
            affectedDomains = {"recipe", "weekly_plan"})
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }
        repository.deleteById(id);
    }

    @Cacheable(value = "recipe_components_by_recipe", key = "#recipeDTO.id")
    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findByParentRecipe(RecipeResponseDTO recipeDTO) {
        if (recipeDTO.getId() == null) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_ID_NOT_PROVIDED));
        }
        return repository.findProjectedByParentRecipeId(recipeDTO.getId()).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findByParentRecipeId(Integer recipeId) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND));
        }
        return repository.findProjectedByParentRecipeId(recipeId).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private void updateEntity(RecipeComponent component, RecipeComponentRequestDTO requestDTO) {

        recipeComponentMapper.updateEntity(requestDTO, component);

        Product product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND)));
        component.setProduct(product);

        if (requestDTO.getRecipeId() != null) {
            Recipe recipe = recipeRepository.findById(requestDTO.getRecipeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND)));
            component.setParentRecipe(recipe);
        } else {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_ID_NULL));
        }
    }
}
