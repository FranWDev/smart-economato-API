package com.economato.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product", indexes = {
                @Index(name = "idx_product_code", columnList = "product_code", unique = true),
                @Index(name = "idx_product_name", columnList = "name"),
                @Index(name = "idx_product_type", columnList = "type")
}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_code", columnNames = "product_code")
})
public class Product {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "product_id")
        private Integer id;

        @NotBlank(message = "El nombre del producto no puede estar vacío")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        @Column(name = "name", nullable = false, length = 100)
        private String name;

        @Size(max = 50)
        @Column(name = "type", nullable = false, length = 50)
        private String type;

        @NotBlank(message = "La unidad de medida no puede estar vacía")
        @Size(max = 20)
        @Column(name = "unit_of_measure", nullable = false, length = 20)
        private String unit;

        @NotNull(message = "El precio unitario no puede ser nulo")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
        @Digits(integer = 10, fraction = 2, message = "Formato de precio inválido")
        @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
        private BigDecimal unitPrice;

        @NotBlank(message = "El código del producto no puede estar vacío")
        @Size(max = 50)
        @Column(name = "product_code", nullable = false, unique = true, length = 50)
        private String productCode;

        @NotNull(message = "El stock actual no puede ser nulo")
        @DecimalMin(value = "0.0", inclusive = true, message = "El stock no puede ser negativo")
        @Digits(integer = 10, fraction = 3, message = "Formato de stock inválido")
        @Column(name = "current_stock", nullable = false, precision = 10, scale = 3)
        private BigDecimal currentStock;

        @DecimalMin(value = "0.00", message = "El porcentaje de disponibilidad no puede ser negativo")
        @DecimalMax(value = "100.00", message = "El porcentaje de disponibilidad no puede ser mayor a 100")
        @Digits(integer = 3, fraction = 2, message = "Formato de disponibilidad inválido")
        @Column(name = "availability_percentage", precision = 5, scale = 2)
        private BigDecimal availabilityPercentage;

        @DecimalMin(value = "0.0", inclusive = true, message = "El stock mínimo no puede ser negativo")
        @Digits(integer = 10, fraction = 3, message = "Formato de stock mínimo inválido")
        @Column(name = "minimum_stock", nullable = false, precision = 10, scale = 3)
        private BigDecimal minimumStock;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "supplier_id", foreignKey = @ForeignKey(name = "fk_product_supplier"))
        private Supplier supplier;

        @Column(name = "is_hidden", nullable = false)
        private boolean isHidden = false;

        @Version
        @Column(name = "version")
        private Long version;

        @JsonIgnore
        @OneToMany(mappedBy = "product")
        private List<OrderDetail> orderDetails;
}
