package com.economato.inventory.service;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.annotation.RecipeAuditable;
import com.economato.inventory.annotation.RecipeCookingAuditable;
import com.economato.inventory.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.dto.response.RecipeStatsResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.exception.ResourceNotFoundException;
import com.economato.inventory.mapper.RecipeMapper;
import com.economato.inventory.mapper.StatsMapper;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.RecipeComponent;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.AllergenRepository;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.security.SecurityContextHelper;

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
    private final I18nService i18nService;

    private final RecipeRepository repository;
    private final ProductRepository productRepository;
    private final AllergenRepository allergenRepository;
    private final RecipeMapper recipeMapper;
    private final StatsMapper statsMapper;
    private final StockLedgerService stockLedgerService;
    private final SecurityContextHelper securityContextHelper;

    public RecipeService(I18nService i18nService, RecipeRepository repository,
            ProductRepository productRepository,
            AllergenRepository allergenRepository,
            RecipeMapper recipeMapper,
            StatsMapper statsMapper,
            StockLedgerService stockLedgerService,
            SecurityContextHelper securityContextHelper) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.allergenRepository = allergenRepository;
        this.recipeMapper = recipeMapper;
        this.statsMapper = statsMapper;
        this.stockLedgerService = stockLedgerService;
        this.securityContextHelper = securityContextHelper;
    }

    @Cacheable(value = "recipes_page_v4", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<RecipeResponseDTO> findAll(Pageable pageable) {
        Page<RecipeResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(recipeMapper::toResponseDTO);
        return new com.economato.inventory.dto.RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "recipe_v4", key = "#id")
    @Transactional(readOnly = true)
    public Optional<RecipeResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id).map(recipeMapper::toResponseDTO);
    }

    @CacheEvict(value = { "recipes_page_v4", "recipe_v4" }, allEntries = true)
    @RecipeAuditable(action = "CREATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeResponseDTO save(RecipeRequestDTO requestDTO) {
        Recipe recipe = toEntity(requestDTO);
        calculateTotalCost(recipe);
        recipe = repository.save(recipe);

        // Return using mapper for consistency with entity state
        return recipeMapper.toResponseDTO(recipe);
    }

    @CacheEvict(value = { "recipes_page_v4", "recipe_v4" }, allEntries = true)
    @RecipeAuditable(action = "UPDATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<RecipeResponseDTO> update(Integer id, RecipeRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    calculateTotalCost(existing);
                    Recipe saved = repository.save(existing);
                    return recipeMapper.toResponseDTO(saved);
                });
    }

    @CacheEvict(value = { "recipes_page_v4", "recipe_v4" }, allEntries = true)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findByNameContaining(String namePart) {
        return repository.findByNameContainingIgnoreCaseAndIsHiddenFalse(namePart).stream()
                .map(recipeMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findByCostLessThan(BigDecimal maxCost) {
        return repository.findByTotalCostLessThanAndIsHiddenFalse(maxCost).stream()
                .map(recipeMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeResponseDTO> findHiddenRecipes(Pageable pageable) {
        return repository.findByIsHiddenTrue(pageable).stream()
                .map(recipeMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = { "recipes_page_v4", "recipe_v4" }, allEntries = true)
    @RecipeAuditable(action = "TOGGLE_HIDDEN")
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleRecipeHiddenStatus(Integer id, boolean hidden) {
        Recipe recipe = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con ID: " + id));

        recipe.setHidden(hidden);
        repository.save(recipe);
    }

    private Recipe toEntity(RecipeRequestDTO requestDTO) {
        Recipe recipe = recipeMapper.toEntity(requestDTO);
        updateEntityCollections(recipe, requestDTO);
        return recipe;
    }

    private void updateEntity(Recipe recipe, RecipeRequestDTO requestDTO) {
        recipeMapper.updateEntity(requestDTO, recipe);
        updateEntityCollections(recipe, requestDTO);
    }

    private void updateEntityCollections(Recipe recipe, RecipeRequestDTO requestDTO) {

        if (recipe.getComponents() == null) {
            recipe.setComponents(new java.util.ArrayList<>());
        }
        if (recipe.getAllergens() == null) {
            recipe.setAllergens(new HashSet<>());
        }

        if (requestDTO.getComponents() != null && !requestDTO.getComponents().isEmpty()) {

            List<RecipeComponentRequestDTO> mergedComponents = requestDTO.getComponents().stream()
                    .collect(Collectors.groupingBy(RecipeComponentRequestDTO::getProductId))
                    .values().stream()
                    .map(group -> {
                        RecipeComponentRequestDTO merged = new RecipeComponentRequestDTO();
                        merged.setProductId(group.get(0).getProductId());
                        merged.setRecipeId(group.get(0).getRecipeId());
                        merged.setQuantity(group.stream()
                                .map(RecipeComponentRequestDTO::getQuantity)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
                        return merged;
                    })
                    .toList();

            var requestedProductIds = mergedComponents.stream()
                    .map(RecipeComponentRequestDTO::getProductId)
                    .collect(Collectors.toSet());

            recipe.getComponents().removeIf(existing -> !requestedProductIds.contains(existing.getProduct().getId()));

            for (RecipeComponentRequestDTO componentDTO : mergedComponents) {
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

    @Transactional(readOnly = true)
    public RecipeStatsResponseDTO getRecipeStats() {
        long total = repository.countByIsHiddenFalse();
        long withAllergens = repository.countWithAllergens();
        long withoutAllergens = repository.countWithoutAllergens();
        BigDecimal averagePrice = repository.getAveragePrice();

        return statsMapper.toRecipeStatsDTO(total, withAllergens, withoutAllergens, averagePrice);
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
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NO_COMPONENTS));
        }

        User currentUser = securityContextHelper.getCurrentUser();

        for (RecipeComponent component : recipe.getComponents()) {
            Product product = productRepository.findById(component.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + component.getProduct().getId()));

            BigDecimal requiredQuantity = component.getQuantity().multiply(cookingRequest.getQuantity());

            // Calcular stock utilizable considerando el porcentaje de disponibilidad
            BigDecimal availabilityPercent = product.getAvailabilityPercentage() != null
                    ? product.getAvailabilityPercentage()
                    : BigDecimal.valueOf(100.00);

            BigDecimal usableStock = product.getCurrentStock()
                    .multiply(availabilityPercent)
                    .divide(BigDecimal.valueOf(100), 3, java.math.RoundingMode.DOWN);

            if (usableStock.compareTo(requiredQuantity) < 0) {
                String errorMessage = String.format(
                        "Stock insuficiente del producto '%s'. Stock total: %s %s, Stock utilizable (%s%%): %s %s, Requerido: %s %s",
                        product.getName(),
                        product.getCurrentStock(),
                        product.getUnit(),
                        availabilityPercent,
                        usableStock,
                        product.getUnit(),
                        requiredQuantity,
                        product.getUnit());
                throw new InvalidOperationException(errorMessage);
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
}
