package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "recipe_component",
    indexes = {
        @Index(name = "idx_recipe_component_recipe", columnList = "parent_recipe_id"),
        @Index(name = "idx_recipe_component_product", columnList = "product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_recipe_component", 
            columnNames = {"parent_recipe_id", "product_id"}
        )
    }
)
public class RecipeComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NotNull(message = "{validation.recipeComponent.parentRecipe.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_recipe_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recipe_component_recipe"))
    private Recipe parentRecipe;

    @NotNull(message = "{validation.recipeComponent.product.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recipe_component_product"))
    private Product product;

    @NotNull(message = "{validation.recipeComponent.quantity.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.recipeComponent.quantity.decimalMin}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;
}
