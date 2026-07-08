package com.economato.inventory.application.usecase.mcp.mcp;
import com.economato.inventory.application.dto.mcp.mcp.McpSearchResultDto;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;


import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class McpSearchService {

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;

    public McpSearchService(ProductRepository productRepository, 
                            RecipeRepository recipeRepository) {
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public McpSearchResultDto unifiedSearch(String query) {
        if (query == null || query.isBlank()) {
            return McpSearchResultDto.builder()
                    .products(List.of())
                    .recipes(List.of())
                    .build();
        }

        List<McpProductDto> products = productRepository.findAll().stream()
                .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase()) || 
                             p.getProductCode().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .map(this::mapToProductDto)
                .collect(Collectors.toList());

        List<McpRecipeDto> recipes = recipeRepository.findAllWithAllergens().stream()
                .filter(r -> r.getName().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .map(this::mapToRecipeDto)
                .collect(Collectors.toList());

        return McpSearchResultDto.builder()
                .products(products)
                .recipes(recipes)
                .build();
    }

    private McpProductDto mapToProductDto(Product p) {
        return McpProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .code(p.getProductCode())
                .stock(p.getCurrentStock())
                .unit(p.getUnit())
                .price(p.getUnitPrice())
                .build();
    }

    private McpRecipeDto mapToRecipeDto(Recipe r) {
        return McpRecipeDto.builder()
                .id(r.getId())
                .name(r.getName())
                .code(r.getId().toString())
                .cost(r.getTotalCost())
                .allergenCount(r.getAllergens().size())
                .description(r.getPresentation())
                .preparation(r.getElaboration())
                .build();
    }
}
