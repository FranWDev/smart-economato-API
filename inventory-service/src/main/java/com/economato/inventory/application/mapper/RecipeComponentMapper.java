package com.economato.inventory.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.application.dto.projection.RecipeComponentProjection;
import com.economato.inventory.application.dto.projection.RecipeProjection;
import com.economato.inventory.application.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.domain.model.RecipeComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;


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

        BigDecimal qty = summary.getQuantity();
        BigDecimal price = summary.getProduct().getUnitPrice();
        BigDecimal pct = summary.getProduct().getAvailabilityPercentage() != null
                ? summary.getProduct().getAvailabilityPercentage()
                : new BigDecimal("100.00");

        if (pct.compareTo(BigDecimal.ZERO) <= 0) {
            return qty.multiply(price);
        }

        return qty.multiply(new BigDecimal("100"))
                .divide(pct, 4, RoundingMode.HALF_UP)
                .multiply(price);
    }


    @Named("calculateSubtotal")
    default BigDecimal calculateSubtotal(RecipeComponent component) {
        if (component.getQuantity() == null || component.getProduct() == null ||
                component.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal qty = component.getQuantity();
        BigDecimal price = component.getProduct().getUnitPrice();
        BigDecimal pct = component.getProduct().getAvailabilityPercentage() != null
                ? component.getProduct().getAvailabilityPercentage()
                : new BigDecimal("100.00");

        if (pct.compareTo(BigDecimal.ZERO) <= 0) {
            return qty.multiply(price);
        }

        return qty.multiply(new BigDecimal("100"))
                .divide(pct, 4, RoundingMode.HALF_UP)
                .multiply(price);
    }


    @Mapping(source = "parentRecipe.id", target = "parentRecipeId")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = ".", target = "subtotal", qualifiedByName = "calculateSubtotalFromProjection")
    RecipeComponentResponseDTO toResponseDTO(
            RecipeComponentProjection projection);

    @Named("calculateSubtotalFromProjection")
    default BigDecimal calculateSubtotalFromProjection(
            RecipeComponentProjection projection) {
        if (projection.getQuantity() == null || projection.getProduct() == null ||
                projection.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal qty = projection.getQuantity();
        BigDecimal price = projection.getProduct().getUnitPrice();
        BigDecimal pct = projection.getProduct().getAvailabilityPercentage() != null
                ? projection.getProduct().getAvailabilityPercentage()
                : new BigDecimal("100.00");

        if (pct.compareTo(BigDecimal.ZERO) <= 0) {
            return qty.multiply(price);
        }

        return qty.multiply(new BigDecimal("100"))
                .divide(pct, 4, RoundingMode.HALF_UP)
                .multiply(price);
    }


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "parentRecipe", ignore = true)
    RecipeComponent toEntity(RecipeComponentRequestDTO requestDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "parentRecipe", ignore = true)
    void updateEntity(RecipeComponentRequestDTO requestDTO, @MappingTarget RecipeComponent component);
}
