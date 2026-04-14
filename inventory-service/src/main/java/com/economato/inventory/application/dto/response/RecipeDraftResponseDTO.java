package com.economato.inventory.application.dto.response;

import com.economato.inventory.application.dto.request.RecipeComponentRequestDTO;
import com.economato.inventory.domain.model.RecipeDraftStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta con los datos de un borrador de receta")
public class RecipeDraftResponseDTO {

    private Integer id;
    private String name;
    private String elaboration;
    private String presentation;
    private BigDecimal portions;
    private List<RecipeComponentRequestDTO> components;
    private List<Integer> allergenIds;
    private boolean isHidden;
    private RecipeDraftStatus status;
    private String createdByName;
    private Integer createdById;
    private String reviewedByName;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;
    private Integer approvedRecipeId;
}