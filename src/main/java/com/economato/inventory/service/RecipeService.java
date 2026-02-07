package com.economato.inventory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.annotation.RecipeAuditable;
import com.economato.inventory.annotation.RecipeCookingAuditable;
import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.RecipeMapper;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeComponent;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.AllergenRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class, RuntimeException.class,
        Exception.class })
public class RecipeService {

    private final RecipeRepository repository;
    private final ProductRepository productRepository;
    private final AllergenRepository allergenRepository;
    private final RecipeMapper recipeMapper;
    private final StockLedgerService stockLedgerService;
    private final UserRepository userRepository;

    public RecipeService(RecipeRepository repository,
            ProductRepository productRepository,
            AllergenRepository allergenRepository,
            RecipeMapper recipeMapper,
            StockLedgerService stockLedgerService,
            UserRepository userRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.allergenRepository = allergenRepository;
        this.recipeMapper = recipeMapper;
        this.stockLedgerService = stockLedgerService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).stream()
                .map(recipeMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "recipe", key = "#id")
    @Transactional(readOnly = true)
    public Optional<RecipeResponseDTO> findById(Integer id) {
        return repository.findByIdWithDetails(id).map(recipeMapper::toResponseDTO);
    }

    @CacheEvict(value = { "recipes", "recipe" }, allEntries = true)
    @RecipeAuditable(action = "CREATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeResponseDTO save(RecipeRequestDTO requestDTO) {
        Recipe recipe = toEntity(requestDTO);
        calculateTotalCost(recipe);
        return recipeMapper.toResponseDTO(repository.save(recipe));
    }

    @CacheEvict(value = { "recipes", "recipe" }, allEntries = true)
    @RecipeAuditable(action = "UPDATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<RecipeResponseDTO> update(Integer id, RecipeRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    calculateTotalCost(existing);
                    return recipeMapper.toResponseDTO(repository.save(existing));
                });
    }

    @CacheEvict(value = { "recipes", "recipe" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findByNameContaining(String namePart) {
        return repository.findByNameContainingIgnoreCaseWithDetails(namePart).stream()
                .map(recipeMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findByCostLessThan(BigDecimal maxCost) {
        return repository.findByTotalCostLessThanWithDetails(maxCost).stream()
                .map(recipeMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    private Recipe toEntity(RecipeRequestDTO requestDTO) {
        Recipe recipe = new Recipe();
        updateEntity(recipe, requestDTO);
        return recipe;
    }

    private void updateEntity(Recipe recipe, RecipeRequestDTO requestDTO) {

        recipe.setName(requestDTO.getName());
        recipe.setElaboration(requestDTO.getElaboration());
        recipe.setPresentation(requestDTO.getPresentation());

        if (requestDTO.getComponents() != null && !requestDTO.getComponents().isEmpty()) {

            var requestedProductIds = requestDTO.getComponents().stream()
                    .map(RecipeComponentRequestDTO::getProductId)
                    .collect(java.util.stream.Collectors.toSet());

            recipe.getComponents().removeIf(existing -> !requestedProductIds.contains(existing.getProduct().getId()));

            for (RecipeComponentRequestDTO componentDTO : requestDTO.getComponents()) {
                Product product = productRepository.findById(componentDTO.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

                RecipeComponent existingComponent = recipe.getComponents().stream()
                        .filter(c -> c.getProduct().getId().equals(componentDTO.getProductId()))
                        .findFirst()
                        .orElse(null);

                if (existingComponent != null) {

                    existingComponent.setQuantity(componentDTO.getQuantity());
                } else {

                    RecipeComponent newComponent = new RecipeComponent();
                    newComponent.setProduct(product);
                    newComponent.setQuantity(componentDTO.getQuantity());
                    recipe.addComponent(newComponent);
                }
            }
        } else {

            recipe.getComponents().clear();
        }

        if (requestDTO.getAllergenIds() != null && !requestDTO.getAllergenIds().isEmpty()) {
            recipe.setAllergens(new HashSet<>(allergenRepository.findAllById(requestDTO.getAllergenIds())));
        } else {
            recipe.getAllergens().clear();
        }
    }

    private void calculateTotalCost(Recipe recipe) {
        BigDecimal totalCost = recipe.getComponents().stream()
                .map(component -> component.getQuantity().multiply(component.getProduct().getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        recipe.setTotalCost(totalCost);
    }

    @RecipeCookingAuditable(action = "COOK_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeResponseDTO cookRecipe(RecipeCookingRequestDTO cookingRequest) {
        log.info("Iniciando proceso de cocinado de receta: recipeId={}, cantidad={}",
                cookingRequest.getRecipeId(), cookingRequest.getQuantity());

        Recipe recipe = repository.findByIdWithDetails(cookingRequest.getRecipeId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Receta no encontrada: " + cookingRequest.getRecipeId()));

        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            throw new InvalidOperationException("La receta no tiene componentes definidos");
        }

        User currentUser = getCurrentUser();

        for (RecipeComponent component : recipe.getComponents()) {
            Product product = productRepository.findById(component.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + component.getProduct().getId()));

            BigDecimal requiredQuantity = component.getQuantity().multiply(cookingRequest.getQuantity());

            if (product.getCurrentStock().compareTo(requiredQuantity) < 0) {
                throw new InvalidOperationException(
                        String.format("Stock insuficiente del producto '%s'. Disponible: %s, Requerido: %s",
                                product.getName(),
                                product.getCurrentStock(),
                                requiredQuantity));
            }

            stockLedgerService.recordStockMovement(
                    product.getId(),
                    requiredQuantity.negate(),
                    MovementType.SALIDA,
                    String.format("Cocinado de receta '%s' - Cantidad: %s", recipe.getName(),
                            cookingRequest.getQuantity()),
                    currentUser,
                    null);

            log.info("Stock descontado del ledger: producto={}, cantidad={}",
                    product.getName(), requiredQuantity);
        }

        log.info("Receta cocinada exitosamente: receta={}, cantidad={}, usuario={}",
                recipe.getName(), cookingRequest.getQuantity(),
                currentUser != null ? currentUser.getName() : "Sistema");

        return recipeMapper.toResponseDTO(recipe);
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                return userRepository.findByName(username).orElse(null);
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener usuario actual: {}", e.getMessage());
        }
        return null;
    }
}
