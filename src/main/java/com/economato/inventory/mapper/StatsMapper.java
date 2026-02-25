package com.economato.inventory.mapper;

import com.economato.inventory.dto.projection.RoleCountProjection;
import com.economato.inventory.dto.response.RecipeStatsResponseDTO;
import com.economato.inventory.dto.response.UserStatsResponseDTO;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface StatsMapper {

    default RecipeStatsResponseDTO toRecipeStatsDTO(long total, long withAllergens, long withoutAllergens,
            BigDecimal averagePrice) {
        return RecipeStatsResponseDTO.builder()
                .totalRecipes(total)
                .recipesWithAllergens(withAllergens)
                .recipesWithoutAllergens(withoutAllergens)
                .averagePrice(averagePrice != null ? averagePrice : BigDecimal.ZERO)
                .build();
    }

    default UserStatsResponseDTO toUserStatsDTO(long total, List<RoleCountProjection> counts) {
        Map<String, Long> usersByRole = counts.stream()
                .collect(Collectors.toMap(
                        p -> p.getRole().name(),
                        RoleCountProjection::getCount));

        return UserStatsResponseDTO.builder()
                .totalUsers(total)
                .usersByRole(usersByRole)
                .build();
    }
}
