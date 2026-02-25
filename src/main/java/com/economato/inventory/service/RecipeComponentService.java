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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeComponentService {

    private final RecipeComponentRepository repository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;

    public RecipeComponentService(
            RecipeComponentRepository repository,
            ProductRepository productRepository,
            RecipeRepository recipeRepository,
            RecipeComponentMapper recipeComponentMapper) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeComponentResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RecipeComponentResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(this::toResponseDTO);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeComponentResponseDTO save(RecipeComponentRequestDTO requestDTO) {
        RecipeComponent component = toEntity(requestDTO);
        repository.save(component);

        // recargar con proyección
        return repository.findProjectedById(component.getId())
                .map(this::toResponseDTO)
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
                            .map(this::toResponseDTO)
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
            throw new ResourceNotFoundException("Recipe ID not provided");
        }
        return repository.findProjectedByParentRecipeId(recipeDTO.getId()).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convierte una proyección de RecipeComponent a RecipeComponentResponseDTO.
     */
    private RecipeComponentResponseDTO toResponseDTO(
            com.economato.inventory.dto.projection.RecipeComponentProjection projection) {
        RecipeComponentResponseDTO dto = new RecipeComponentResponseDTO();
        dto.setId(projection.getId());
        dto.setQuantity(projection.getQuantity());

        if (projection.getParentRecipe() != null) {
            dto.setParentRecipeId(projection.getParentRecipe().getId());
        }

        if (projection.getProduct() != null) {
            dto.setProductId(projection.getProduct().getId());
            dto.setProductName(projection.getProduct().getName());

            BigDecimal price = projection.getProduct().getUnitPrice();
            if (price != null && projection.getQuantity() != null) {
                dto.setSubtotal(price.multiply(projection.getQuantity()));
            }
        }

        return dto;
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
