package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(
    name = "allergen",
    indexes = {
        @Index(name = "idx_allergen_name", columnList = "name", unique = true)
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_allergen_name", columnNames = "name")
    }
)
public class Allergen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allergen_id")
    private Integer id;

    @NotBlank(message = "El nombre del alérgeno no puede estar vacío")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @ManyToMany(mappedBy = "allergens")
    @JsonBackReference
    private List<Recipe> recipes;
}
