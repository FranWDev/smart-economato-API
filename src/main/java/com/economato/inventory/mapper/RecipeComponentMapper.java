package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.projection.RecipeProjection;
import com.economato.inventory.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.model.RecipeComponent;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecipeComponentMapper {

    @Mapping(source = "parentRecipe.id", target = "parentRecipeId")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = ".", target = "subtotal", qualifiedByName = "calculateSubtotal")
    RecipeComponentResponseDTO toResponseDTO(RecipeComponent component);

    @Mapping(target = "parentRecipeId", ignore = true)
    @Mapping(source = "summary.product.id", target = "productId")
    @Mapping(source = "summary.product.name", target = "productName")
    @Mapping(source = "summary", target = "subtotal", qualifiedByName = "calculateSubtotalFromSummary")
    RecipeComponentResponseDTO toResponseDTO(RecipeProjection.RecipeComponentSummary summary);

    @Named("calculateSubtotalFromSummary")
    default BigDecimal calculateSubtotalFromSummary(RecipeProjection.RecipeComponentSummary summary) {
        if (summary.getQuantity() == null || summary.getProduct() == null ||
                summary.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return summary.getQuantity().multiply(summary.getProduct().getUnitPrice());
    }

    @Named("calculateSubtotal")
    default BigDecimal calculateSubtotal(RecipeComponent component) {
        if (component.getQuantity() == null || component.getProduct() == null ||
                component.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return component.getQuantity().multiply(component.getProduct().getUnitPrice());
    }
}
