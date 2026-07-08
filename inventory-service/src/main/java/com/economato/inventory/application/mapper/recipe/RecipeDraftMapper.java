package com.economato.inventory.application.mapper.recipe;

import com.economato.inventory.application.dto.recipe.request.RecipeComponentRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeDraftRequestDTO;
import com.economato.inventory.application.dto.recipe.request.RecipeRequestDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeDraftResponseDTO;
import com.economato.inventory.domain.model.recipe.RecipeDraft;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecipeDraftMapper {

    @Mapping(target = "components", ignore = true)
    @Mapping(target = "allergenIds", ignore = true)
    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "reviewedByName", ignore = true)
    RecipeDraftResponseDTO toResponseDTO(RecipeDraft draft);

    default RecipeRequestDTO toRecipeRequestDTO(RecipeDraft draft, ObjectMapper objectMapper) {
        RecipeRequestDTO request = new RecipeRequestDTO();
        request.setName(draft.getName());
        request.setElaboration(draft.getElaboration());
        request.setPresentation(draft.getPresentation());
        request.setPortions(draft.getPortions());
        request.setHidden(draft.isHidden());
        request.setComponents(readComponents(draft.getComponentsJson(), objectMapper));
        request.setAllergenIds(readAllergenIds(draft.getAllergenIdsJson(), objectMapper));
        return request;
    }

    default List<RecipeComponentRequestDTO> readComponents(String json, ObjectMapper objectMapper) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<RecipeComponentRequestDTO>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize recipe draft components", e);
        }
    }

    default List<Integer> readAllergenIds(String json, ObjectMapper objectMapper) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize recipe draft allergens", e);
        }
    }

    default String writeJson(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize recipe draft data", e);
        }
    }
}