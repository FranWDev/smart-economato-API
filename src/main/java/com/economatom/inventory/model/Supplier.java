package com.economatom.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(
    name = "supplier",
    indexes = {
        @Index(name = "idx_supplier_name", columnList = "name", unique = true)
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_name", columnNames = "name")
    }
)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Integer id;

    @NotBlank(message = "El nombre del proveedor no puede estar vacío")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "supplier")
    private List<Product> products;
}
