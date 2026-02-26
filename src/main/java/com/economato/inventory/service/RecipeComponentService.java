package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.RecipeComponentMapper;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeComponent;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeComponentRepository;
import com.economato.inventory.repository.RecipeRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RecipeComponentResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(recipeComponentMapper::toResponseDTO);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeComponentResponseDTO save(RecipeComponentRequestDTO requestDTO) {
        RecipeComponent component = new RecipeComponent();
        updateEntity(component, requestDTO);
        repository.save(component);

        // recargar con proyección
        return repository.findProjectedById(component.getId())
                .map(recipeComponentMapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Saved component not found"));
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<RecipeComponentResponseDTO> update(Integer id, RecipeComponentRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    repository.save(existing);

                    return repository.findProjectedById(existing.getId())
                            .map(recipeComponentMapper::toResponseDTO)
                            .orElseThrow(() -> new RuntimeException("Updated component not found"));
                });
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findByParentRecipe(RecipeResponseDTO recipeDTO) {
        if (recipeDTO.getId() == null) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_ID_NOT_PROVIDED));
        }
        return repository.findProjectedByParentRecipeId(recipeDTO.getId()).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private void updateEntity(RecipeComponent component, RecipeComponentRequestDTO requestDTO) {
        // Asignar propiedades simples del DTO usando el mapper
        recipeComponentMapper.updateEntity(requestDTO, component);

        // Asignar el producto
        Product product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        component.setProduct(product);

        // Asignar la receta
        if (requestDTO.getRecipeId() != null) {
            Recipe recipe = recipeRepository.findById(requestDTO.getRecipeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
            component.setParentRecipe(recipe);
        } else {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_ID_NULL));
        }
    }
}
