package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.RecipeAuditable;
import com.economato.inventory.domain.RecipeCookingAuditable;
import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.response.RecipeAverageCostResponseDTO;
import com.economato.inventory.application.dto.response.RecipeCountResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.response.RecipeStatsResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.application.mapper.RecipeMapper;
import com.economato.inventory.application.mapper.StatsMapper;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeComponent;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AllergenRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;

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
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;

    public RecipeService(I18nService i18nService, RecipeRepository repository,
            ProductRepository productRepository,
            AllergenRepository allergenRepository,
            RecipeMapper recipeMapper,
            StatsMapper statsMapper,
            StockLedgerService stockLedgerService,
            SecurityContextHelper securityContextHelper,
            RecipeCookingAuditRepository recipeCookingAuditRepository) {
        this.i18nService = i18nService;
        this.repository = repository;
        this.productRepository = productRepository;
        this.allergenRepository = allergenRepository;
        this.recipeMapper = recipeMapper;
        this.statsMapper = statsMapper;
        this.stockLedgerService = stockLedgerService;
        this.securityContextHelper = securityContextHelper;
        this.recipeCookingAuditRepository = recipeCookingAuditRepository;
    }

    @Cacheable(value = "recipes_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<RecipeResponseDTO> findAll(Pageable pageable) {
        Page<RecipeResponseDTO> page = repository.findByIsHiddenFalse(pageable)
                .map(recipeMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @Cacheable(value = "recipe", key = "#id")
    @Transactional(readOnly = true)
    public Optional<RecipeResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id).map(recipeMapper::toResponseDTO);
    }

    @CacheEvict(value = { "recipes_page", "recipe" }, allEntries = true)
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

    @CacheEvict(value = { "recipes_page", "recipe" }, allEntries = true)
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

    @CacheEvict(value = { "recipes_page", "recipe" }, allEntries = true)
    @Deprecated(since = "2026-03", forRemoval = false)
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
    public Page<RecipeResponseDTO> findHiddenRecipes(Pageable pageable) {
        Page<RecipeResponseDTO> page = repository.findByIsHiddenTrue(pageable)
                .map(recipeMapper::toResponseDTO);
        return new RestPage<>(page.getContent(), page.getPageable(),
                page.getTotalElements());
    }

    @CacheEvict(value = { "recipes_page", "recipe" }, allEntries = true)
    @RecipeAuditable(action = "TOGGLE_HIDDEN")
    @Transactional(rollbackFor = { ResourceNotFoundException.class, InvalidOperationException.class })
    public void toggleRecipeHiddenStatus(Integer id, boolean hidden) {
        Recipe recipe = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND, new Object[] { id })));

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
                        .orElseThrow(() -> new ResourceNotFoundException(
                                i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[] { componentDTO.getProductId() })));

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

    @Transactional(readOnly = true)
    public RecipeCountResponseDTO getRecipesWithAllergensCount() {
        return statsMapper.toRecipeCountDTO(repository.countWithAllergens());
    }

    @Transactional(readOnly = true)
    public RecipeCountResponseDTO getRecipesWithoutAllergensCount() {
        return statsMapper.toRecipeCountDTO(repository.countWithoutAllergens());
    }

    @Transactional(readOnly = true)
    public RecipeAverageCostResponseDTO getRecipesAverageCost() {
        return statsMapper.toRecipeAverageCostDTO(repository.getAveragePrice());
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
        String correlationId = java.util.UUID.randomUUID().toString();
        cookingRequest.setCorrelationId(correlationId);

        log.info("Iniciando proceso de cocinado de receta: recipeId={}, cantidad={}, correlationId={}",
                cookingRequest.getRecipeId(), cookingRequest.getQuantity(), correlationId);

        Recipe recipe = repository.findByIdWithDetails(cookingRequest.getRecipeId())
                .orElseThrow(
                        () -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NOT_FOUND, new Object[] { cookingRequest.getRecipeId() })));

        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RECIPE_NO_COMPONENTS));
        }

        User currentUser = securityContextHelper.getCurrentUser();

        for (RecipeComponent component : recipe.getComponents()) {
            Product product = productRepository.findById(component.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            i18nService.getMessage(MessageKey.ERROR_PRODUCT_NOT_FOUND, new Object[] { component.getProduct().getId() })));

            BigDecimal requiredQuantity = component.getQuantity().multiply(cookingRequest.getQuantity());

            // Calcular stock utilizable considerando el porcentaje de disponibilidad
            BigDecimal availabilityPercent = product.getAvailabilityPercentage() != null
                    ? product.getAvailabilityPercentage()
                    : BigDecimal.valueOf(100.00);

            BigDecimal usableStock = product.getCurrentStock()
                    .multiply(availabilityPercent)
                    .divide(BigDecimal.valueOf(100), 3, java.math.RoundingMode.DOWN);

            if (usableStock.compareTo(requiredQuantity) < 0) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_RECIPE_STOCK_INSUFFICIENT,
                                new Object[] { product.getName(), requiredQuantity, usableStock }));
            }

            stockLedgerService.recordStockMovement(
                    product.getId(),
                    requiredQuantity.negate(),
                    MovementType.SALIDA,
                    i18nService.getMessage(MessageKey.LEDGER_DESCRIPTION_COOKING,
                                    new Object[] { recipe.getName(), cookingRequest.getQuantity() }),
                    currentUser,
                    null,
                    null,
                    correlationId);

            log.info("Stock descontado del ledger: producto={}, cantidad={}",
                    product.getName(), requiredQuantity);
        }

        log.info("Receta cocinada exitosamente: receta={}, cantidad={}, usuario={}",
                recipe.getName(), cookingRequest.getQuantity(),
                currentUser != null ? currentUser.getName() : "Sistema");

        return recipeMapper.toResponseDTO(recipe);
    }

    /**
     * Revierte un cocinado de receta específico.
     */
    @Transactional(rollbackFor = Exception.class)
    public void revertCooking(Long auditId, String reason) {
        log.info("Iniciando reversión de cocinado: auditId={}, motivo={}", auditId, reason);
        
        try {
            var audit = recipeCookingAuditRepository.findById(auditId)
                    .orElseThrow(() -> new ResourceNotFoundException("Auditoría de cocinado no encontrada (ID: " + auditId + ")"));
            
            if (audit.getCorrelationId() == null) {
                log.warn("Intento de revertir audi toría sin correlationId: auditId={}", auditId);
                throw new InvalidOperationException("Esta audi toría no tiene ID de correlación y no puede revertirse automáticamente.");
            }

            // Prevenir doble reversión: verificar si ya fue revertido antes
            if (audit.getDetails() != null && audit.getDetails().contains("[REVERTIDO:")) {
                throw new InvalidOperationException(
                    i18nService.getMessage(com.economato.inventory.infrastructure.config.web.MessageKey.ERROR_COOKING_ALREADY_REVERTED));
            }

            stockLedgerService.revertMovement(audit.getCorrelationId(), "Deshacer cocinado: " + reason);
            
            // Marcar la auditoría como revertida en los detalles
            String previousDetails = audit.getDetails() != null ? audit.getDetails() : "";
            audit.setDetails(previousDetails + " [REVERTIDO: " + reason + "]");
            recipeCookingAuditRepository.save(audit);
            
            log.info("Cocinado revertido exitosamente: auditId={}, correlationId={}", auditId, audit.getCorrelationId());
        } catch (ResourceNotFoundException | InvalidOperationException e) {
            log.warn("Error validado al revertir cocinado: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al revertir cocinado: {}", e.getMessage(), e);
            throw e; // Permitir que ruede hasta el GlobalExceptionHandler
        }
    }
}
