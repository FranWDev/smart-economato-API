package com.economato.inventory.application.usecase.recipe;

import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.economato.inventory.application.dto.recipe.request.RecipeQuantityRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequirementsRequestDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanStockRequirementDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.shared.RestPage;
import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.recipe.response.CookableRecipeResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeAverageCostResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeCountResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeStatsResponseDTO;
import com.economato.inventory.application.mapper.recipe.CookableRecipeMapper;
import com.economato.inventory.application.mapper.recipe.RecipeMapper;
import com.economato.inventory.application.mapper.shared.StatsMapper;
import com.economato.inventory.domain.recipe.RecipeAuditable;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.weeklyplan.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import lombok.extern.slf4j.Slf4j;

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
    private final CookableRecipeMapper cookableRecipeMapper;
    private final StatsMapper statsMapper;
    private final StockLedgerService stockLedgerService;
    private final WeeklyPlanStockReservationService weeklyPlanStockReservationService;
    private final SecurityContextHelper securityContextHelper;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;

    private final RecipeCostCalculator recipeCostCalculator;
    private final RecipeCookingProcessor recipeCookingProcessor;

    public RecipeService(I18nService i18nService, RecipeRepository repository,
            ProductRepository productRepository,
            AllergenRepository allergenRepository,
            RecipeMapper recipeMapper,
            CookableRecipeMapper cookableRecipeMapper,
            StatsMapper statsMapper,
            StockLedgerService stockLedgerService,
            WeeklyPlanStockReservationService weeklyPlanStockReservationService,
            SecurityContextHelper securityContextHelper,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            WeeklyPlanSlotRepository weeklyPlanSlotRepository,
            WeeklyPlanRepository weeklyPlanRepository) {
        this(i18nService, repository, productRepository, allergenRepository, recipeMapper,
             cookableRecipeMapper, statsMapper, stockLedgerService, weeklyPlanStockReservationService,
             securityContextHelper, recipeCookingAuditRepository, weeklyPlanSlotRepository, weeklyPlanRepository,
             new RecipeCostCalculator(repository, statsMapper),
             new RecipeCookingProcessor(i18nService, repository, productRepository, recipeMapper, stockLedgerService,
                                        securityContextHelper, recipeCookingAuditRepository, weeklyPlanSlotRepository,
                                        weeklyPlanRepository));
    }

    @Autowired
    public RecipeService(I18nService i18nService, RecipeRepository repository,
            ProductRepository productRepository,
            AllergenRepository allergenRepository,
            RecipeMapper recipeMapper,
            CookableRecipeMapper cookableRecipeMapper,
            StatsMapper statsMapper,
            StockLedgerService stockLedgerService,
            WeeklyPlanStockReservationService weeklyPlanStockReservationService,
            SecurityContextHelper securityContextHelper,
            RecipeCookingAuditRepository recipeCookingAuditRepository,
            WeeklyPlanSlotRepository weeklyPlanSlotRepository,
            WeeklyPlanRepository weeklyPlanRepository,
            RecipeCostCalculator recipeCostCalculator,
            RecipeCookingProcessor recipeCookingProcessor) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.allergenRepository = allergenRepository;
        this.recipeMapper = recipeMapper;
        this.cookableRecipeMapper = cookableRecipeMapper;
        this.statsMapper = statsMapper;
        this.stockLedgerService = stockLedgerService;
        this.weeklyPlanStockReservationService = weeklyPlanStockReservationService;
        this.securityContextHelper = securityContextHelper;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
        this.weeklyPlanSlotRepository = weeklyPlanSlotRepository;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.recipeCostCalculator = recipeCostCalculator;
        this.recipeCookingProcessor = recipeCookingProcessor;
    }

    @Cacheable(value = "recipes_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<RecipeResponseDTO> findAll(Pageable pageable) {
        Page<RecipeResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(recipeMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "recipe", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<RecipeResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id).map(recipeMapper::toResponseDTO);
    }

    @Caching(evict = {
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "recipe_stats", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true)
    })
    @RealtimeSync(entityType = "recipe", action = "CREATE",
            affectedDomains = {"recipe", "weekly_plan"})
    @RecipeAuditable(action = "CREATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public RecipeResponseDTO save(RecipeRequestDTO requestDTO) {
        Recipe recipe = toEntity(requestDTO);
        recipeCostCalculator.calculateTotalCost(recipe);
        final Recipe savedRecipe = repository.save(recipe);
        final Integer recipeId = savedRecipe.getId();

        return repository.findProjectedById(recipeId)
            .map(projection -> recipeMapper.toResponseDTO(projection))
            .orElseGet(() -> recipeMapper.toResponseDTO(savedRecipe));
    }

    @Caching(evict = {
            @CacheEvict(value = "recipe", key = "#id"),
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "recipe_stats", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true)
    })
    @RealtimeSync(entityType = "recipe", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"recipe", "weekly_plan"})
    @RecipeAuditable(action = "UPDATE_RECIPE")
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public Optional<RecipeResponseDTO> update(Integer id, RecipeRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, requestDTO);
                    recipeCostCalculator.calculateTotalCost(existing);
                    Recipe saved = repository.save(existing);
                    return repository.findProjectedById(saved.getId())
                        .map(projection -> recipeMapper.toResponseDTO(projection))
                        .orElseGet(() -> recipeMapper.toResponseDTO(saved));
                });
    }

    @Caching(evict = {
            @CacheEvict(value = "recipe", key = "#id"),
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "recipe_stats", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true)
    })
    @Deprecated(since = "2026-03", forRemoval = false)
    @Transactional(rollbackFor = { InvalidOperationException.class, ResourceNotFoundException.class,
            RuntimeException.class, Exception.class })
    public void deleteById(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
        }
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

    @Cacheable(value = "cookable_recipes", key = "'all'", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<CookableRecipeResponseDTO> findCookableRecipes() {
        final List<Recipe> recipes = repository.findAllVisibleWithDetails();
        final Set<Integer> productIds = recipes.stream()
            .flatMap(recipe -> recipe.getComponents().stream())
            .map(component -> component.getProduct().getId())
            .collect(Collectors.toSet());
        final Map<Integer, BigDecimal> reservedByProduct = productIds.isEmpty()
            ? Map.of()
            : weeklyPlanStockReservationService.calculateReservedStock(null);

        return recipes.stream()
            .map(recipe -> {
                CookableRecipeResponseDTO dto = cookableRecipeMapper.toResponseDTO(recipe);

                if (dto.getComponents() != null) {
                    for (CookableRecipeResponseDTO.CookableRecipeComponentResponseDTO componentDTO : dto.getComponents()) {
                        BigDecimal currentStock = componentDTO.getAvailableStock() != null ? componentDTO.getAvailableStock() : BigDecimal.ZERO;
                        BigDecimal reserved = reservedByProduct.getOrDefault(componentDTO.getProductId(), BigDecimal.ZERO);

                        RecipeComponent sourceComponent = recipe.getComponents().stream()
                            .filter(component -> component.getProduct().getId().equals(componentDTO.getProductId()))
                            .findFirst()
                            .orElse(null);

                        BigDecimal availabilityPct = sourceComponent != null
                            && sourceComponent.getProduct().getAvailabilityPercentage() != null
                                ? sourceComponent.getProduct().getAvailabilityPercentage()
                                : new BigDecimal("100.00");

                        BigDecimal maxAvailable = currentStock.multiply(availabilityPct)
                            .divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);

                        componentDTO.setAvailableStock(maxAvailable);
                        componentDTO.setGrossAvailableStock(currentStock);
                        componentDTO.setAvailabilityPercentage(availabilityPct);
                        componentDTO.setReservedByOtherPlans(reserved);
                    }
                }

                BigDecimal cookableQuantity = calculateCookableQuantity(recipe, reservedByProduct);
                dto.setCookableQuantity(cookableQuantity);
                dto.setCookable(cookableQuantity.compareTo(BigDecimal.ONE) >= 0);
                return dto;
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WeeklyPlanStockRequirementDTO> calculateRequirements(RecipeRequirementsRequestDTO request) {
        Map<Integer, BigDecimal> netRequirements = new HashMap<>();
        for (RecipeQuantityRequestDTO item : request.getRecipes()) {
            Recipe recipe = repository.findByIdWithDetails(item.getRecipeId())
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND, new Object[]{item.getRecipeId()})));
            
            for (RecipeComponent component : recipe.getComponents()) {
                BigDecimal needed = component.getQuantity().multiply(item.getQuantity())
                    .divide(recipe.getPortions(), 4, RoundingMode.HALF_UP);
                netRequirements.merge(component.getProduct().getId(), needed, BigDecimal::add);
            }
        }

        return weeklyPlanStockReservationService.calculateStockRequirements(netRequirements, null);
    }

    @Transactional(readOnly = true)
    public Page<RecipeResponseDTO> findHiddenRecipes(Pageable pageable) {
        Page<RecipeResponseDTO> page = repository.findByIsHiddenTrue(pageable)
                .map(recipeMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Caching(evict = {
            @CacheEvict(value = "recipe", key = "#id"),
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true)
    })
    @RealtimeSync(entityType = "recipe", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"recipe", "weekly_plan"})
    @RecipeAuditable(action = "TOGGLE_HIDDEN")
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleRecipeHiddenStatus(Integer id, boolean hidden) {
        Recipe recipe = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND, new Object[] { id })));

        recipe.setHidden(hidden);
        repository.save(recipe);
    }

    @Caching(evict = {
            @CacheEvict(value = "recipe", allEntries = true),
            @CacheEvict(value = "recipes_page", allEntries = true),
            @CacheEvict(value = "recipe_stats", allEntries = true),
            @CacheEvict(value = "weekly_plan", allEntries = true),
            @CacheEvict(value = "weekly_plan_requirements", allEntries = true),
            @CacheEvict(value = "student_metrics", allEntries = true),
            @CacheEvict(value = "cookable_recipes", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void recalculateRecipesUsingProduct(Integer productId) {
        log.info("Recalculando costes para recetas que contienen el producto ID: {}", productId);
        List<Recipe> affectedRecipes = repository.findByComponentsProductIdWithDetails(productId);
        if (affectedRecipes.isEmpty()) {
            return;
        }

        for (Recipe recipe : affectedRecipes) {
            recipeCostCalculator.calculateTotalCost(recipe);
        }
        repository.saveAll(affectedRecipes);
        log.info("Se han actualizado {} recetas debido al cambio en el producto {}", affectedRecipes.size(), productId);
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
            recipe.setComponents(new HashSet<>());
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

            Map<Integer, Product> productsById = productRepository.findAllById(requestedProductIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
            if (productsById.size() != requestedProductIds.size()) {
                Set<Integer> missingIds = new HashSet<>(requestedProductIds);
                missingIds.removeAll(productsById.keySet());
                throw new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND,
                                new Object[] { missingIds.iterator().next() }));
            }

            recipe.getComponents().removeIf(existing -> !requestedProductIds.contains(existing.getProduct().getId()));

            for (RecipeComponentRequestDTO componentDTO : mergedComponents) {
                Product product = productsById.get(componentDTO.getProductId());

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

    @Cacheable(value = "recipe_stats", key = "'global'")
    @Transactional(readOnly = true)
    public RecipeStatsResponseDTO getRecipeStats() {
        return recipeCostCalculator.getRecipeStats();
    }

    @Cacheable(value = "recipe_stats", key = "'withAllergens'")
    @Transactional(readOnly = true)
    public RecipeCountResponseDTO getRecipesWithAllergensCount() {
        return recipeCostCalculator.getRecipesWithAllergensCount();
    }

    @Cacheable(value = "recipe_stats", key = "'withoutAllergens'")
    @Transactional(readOnly = true)
    public RecipeCountResponseDTO getRecipesWithoutAllergensCount() {
        return recipeCostCalculator.getRecipesWithoutAllergensCount();
    }

    @Cacheable(value = "recipe_stats", key = "'averageCost'")
    @Transactional(readOnly = true)
    public RecipeAverageCostResponseDTO getRecipesAverageCost() {
        return recipeCostCalculator.getRecipesAverageCost();
    }

    private BigDecimal calculateCookableQuantity(Recipe recipe, Map<Integer, BigDecimal> reservedByProduct) {
        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal minCookable = null;
        for (RecipeComponent component : recipe.getComponents()) {
            if (component.getProduct() == null || component.getQuantity() == null
                    || component.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }

            BigDecimal currentStock = component.getProduct().getCurrentStock() != null
                ? component.getProduct().getCurrentStock()
                : BigDecimal.ZERO;

            BigDecimal availabilityPct = component.getProduct().getAvailabilityPercentage() != null
                ? component.getProduct().getAvailabilityPercentage()
                : BigDecimal.valueOf(100.00);

            BigDecimal maxAvailable = currentStock
                .multiply(availabilityPct)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.DOWN);

            BigDecimal reserved = reservedByProduct.getOrDefault(component.getProduct().getId(), BigDecimal.ZERO);
            BigDecimal available = maxAvailable.subtract(reserved).max(BigDecimal.ZERO);

            BigDecimal cookableByComponent = available
                    .divide(component.getQuantity(), 3, RoundingMode.DOWN);

            minCookable = minCookable == null
                    ? cookableByComponent
                    : minCookable.min(cookableByComponent);
        }

        return (minCookable == null ? BigDecimal.ZERO : minCookable).setScale(3, RoundingMode.DOWN);
    }

    public RecipeResponseDTO cookRecipe(RecipeCookingRequestDTO cookingRequest) {
        return recipeCookingProcessor.cookRecipe(cookingRequest);
    }

    public List<Integer> revertCooking(Long auditId, String reason) {
        return recipeCookingProcessor.revertCooking(auditId, reason);
    }
}
