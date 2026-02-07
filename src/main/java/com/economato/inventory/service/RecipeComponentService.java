package com.economato.inventory.service;

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
@Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
public class RecipeComponentService {

    private final RecipeComponentRepository repository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeComponentMapper recipeComponentMapper;

    public RecipeComponentService(
            RecipeComponentRepository repository,
            ProductRepository productRepository,
            RecipeRepository recipeRepository,
            RecipeComponentMapper recipeComponentMapper) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
        this.recipeComponentMapper = recipeComponentMapper;
    }

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RecipeComponentResponseDTO> findById(Integer id) {
        return repository.findWithRecipeAndProductById(id)
                .map(recipeComponentMapper::toResponseDTO);
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public RecipeComponentResponseDTO save(RecipeComponentRequestDTO requestDTO) {
        RecipeComponent component = toEntity(requestDTO);
        repository.save(component);

        // recargar con relaciones para evitar parentRecipeId null
        RecipeComponent loaded = repository.findWithRecipeAndProductById(component.getId())
                .orElseThrow(() -> new RuntimeException("Saved component not found"));

        return recipeComponentMapper.toResponseDTO(loaded);
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public Optional<RecipeComponentResponseDTO> update(Integer id, RecipeComponentRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    repository.save(existing);

                    RecipeComponent reloaded = repository.findWithRecipeAndProductById(existing.getId())
                            .orElseThrow(() -> new RuntimeException("Updated component not found"));
                    return recipeComponentMapper.toResponseDTO(reloaded);
                });
    }

    @Transactional(rollbackFor = {InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class, Exception.class})
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findByParentRecipe(RecipeResponseDTO recipeDTO) {
        Recipe recipe = recipeRepository.findById(recipeDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        return repository.findAllByRecipeIdWithRelations(recipe.getId()).stream()
                .map(recipeComponentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private RecipeComponent toEntity(RecipeComponentRequestDTO requestDTO) {
        RecipeComponent component = new RecipeComponent();
        updateEntity(component, requestDTO);
        return component;
    }

    private void updateEntity(RecipeComponent component, RecipeComponentRequestDTO requestDTO) {
        // Asignar el producto
        Product product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        component.setProduct(product);
        component.setQuantity(requestDTO.getQuantity());

        // Asignar la receta
        if (requestDTO.getRecipeId() != null) {
            Recipe recipe = recipeRepository.findById(requestDTO.getRecipeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
            component.setParentRecipe(recipe);
        } else {
            throw new InvalidOperationException("Recipe ID must not be null");
        }
    }
}
