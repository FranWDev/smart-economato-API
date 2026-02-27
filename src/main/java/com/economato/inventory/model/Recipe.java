package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "recipe", indexes = {
        @Index(name = "idx_recipe_name", columnList = "recipe_name")
})
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Integer id;

    @NotBlank(message = "{validation.recipe.name.notBlank}")
    @Size(min = 2, max = 150, message = "{validation.recipe.name.size}")
    @Column(name = "recipe_name", nullable = false, length = 150)
    private String name;

    @Size(max = 2000, message = "{validation.recipe.elaboration.size}")
    @Column(name = "elaboration", columnDefinition = "TEXT")
    private String elaboration;

    @Size(max = 1000, message = "{validation.recipe.presentation.size}")
    @Column(name = "presentation", columnDefinition = "TEXT")
    private String presentation;

    @DecimalMin(value = "0.0", inclusive = true, message = "{validation.recipe.totalCost.decimalMin}")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private boolean isHidden = false;

    @Version
    @Column(name = "version")
    private Long version;

    @JsonIgnore
    @OneToMany(mappedBy = "parentRecipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeComponent> components = new ArrayList<>();

    public void addComponent(RecipeComponent component) {
        components.add(component);
        component.setParentRecipe(this);
    }

    public void removeComponent(RecipeComponent component) {
        components.remove(component);
        component.setParentRecipe(null);
    }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "recipe_allergen", joinColumns = @JoinColumn(name = "recipe_id"), inverseJoinColumns = @JoinColumn(name = "allergen_id"))
    @JsonManagedReference
    @Builder.Default
    private Set<Allergen> allergens = new HashSet<>();
}
