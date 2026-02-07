package com.economato.inventory.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recipe_allergen")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeAllergen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allergen_id", nullable = false)
    private Allergen allergen;
}
