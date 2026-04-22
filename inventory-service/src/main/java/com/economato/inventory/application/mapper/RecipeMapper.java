package com.economato.inventory.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.application.dto.projection.RecipeProjection;
import com.economato.inventory.application.dto.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.domain.model.Recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Mapper(componentModel = "spring", uses = { RecipeComponentMapper.class,
        AllergenMapper.class }, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecipeMapper {

    @Mapping(source = "components", target = "components")
    @Mapping(source = "allergens", target = "allergens")
    @Mapping(source = ".", target = "totalCost", qualifiedByName = "calculateTotalCost")
    RecipeResponseDTO toResponseDTO(Recipe recipe);

    @Mapping(source = "projection.id", target = "id")
    @Mapping(source = "projection.name", target = "name")
    @Mapping(source = "projection.elaboration", target = "elaboration")
    @Mapping(source = "projection.presentation", target = "presentation")
    @Mapping(source = "projection.", target = "totalCost", qualifiedByName = "calculateTotalCostFromProjection")
    @Mapping(source = "projection.sellingPrice", target = "sellingPrice")
    @Mapping(source = "projection.portions", target = "portions")

    @Mapping(source = "projection.isHidden", target = "hidden")
    @Mapping(source = "projection.components", target = "components")
    @Mapping(source = "projection.allergens", target = "allergens")
    RecipeResponseDTO toResponseDTO(RecipeProjection projection);

    @Named("calculateTotalCost")
    default BigDecimal calculateTotalCost(Recipe recipe) {
        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return recipe.getComponents().stream()
                .filter(component -> component.getQuantity() != null &&
                        component.getProduct() != null &&
                        component.getProduct().getUnitPrice() != null)
                .map(component -> {
                    BigDecimal qty = component.getQuantity();
                    BigDecimal price = component.getProduct().getUnitPrice();
                    BigDecimal pct = component.getProduct().getAvailabilityPercentage() != null
                            ? component.getProduct().getAvailabilityPercentage()
                            : new BigDecimal("100.00");

                    if (pct.compareTo(BigDecimal.ZERO) <= 0) {
                        return qty.multiply(price);
                    }

                    // Cost = (NetQty * 100 / AvailabilityPct) * Price
                    return qty.multiply(new BigDecimal("100"))
                            .divide(pct, 10, RoundingMode.HALF_UP)
                            .multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Named("calculateTotalCostFromProjection")
    default BigDecimal calculateTotalCostFromProjection(RecipeProjection projection) {
        if (projection.getComponents() == null || projection.getComponents().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return projection.getComponents().stream()
                .filter(component -> component.getQuantity() != null &&
                        component.getProduct() != null &&
                        component.getProduct().getUnitPrice() != null)
                .map(component -> {
                    BigDecimal qty = component.getQuantity();
                    BigDecimal price = component.getProduct().getUnitPrice();
                    BigDecimal pct = component.getProduct().getAvailabilityPercentage() != null
                            ? component.getProduct().getAvailabilityPercentage()
                            : new BigDecimal("100.00");

                    if (pct.compareTo(BigDecimal.ZERO) <= 0) {
                        return qty.multiply(price);
                    }

                    return qty.multiply(new BigDecimal("100"))
                            .divide(pct, 10, RoundingMode.HALF_UP)
                            .multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * Convierte RecipeRequestDTO a Recipe entidad (sin componentes ni alérgenos)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "components", ignore = true)
    @Mapping(target = "allergens", ignore = true)
    @Mapping(target = "version", ignore = true)
    Recipe toEntity(RecipeRequestDTO requestDTO);

    /**
     * Actualiza una entidad Recipe existente con datos del DTO (sin componentes ni
     * alérgenos)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "components", ignore = true)
    @Mapping(target = "allergens", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(RecipeRequestDTO requestDTO, @MappingTarget Recipe recipe);
}
