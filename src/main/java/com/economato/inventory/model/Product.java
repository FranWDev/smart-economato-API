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

        @NotBlank(message = "{validation.product.name.notBlank}")
        @Size(min = 2, max = 100, message = "{validation.product.name.size}")
        @Column(name = "name", nullable = false, length = 100)
        private String name;

        @Size(max = 50)
        @Column(name = "type", nullable = false, length = 50)
        private String type;

        @NotBlank(message = "{validation.product.unit.notBlank}")
        @Size(max = 20)
        @Column(name = "unit_of_measure", nullable = false, length = 20)
        private String unit;

        @NotNull(message = "{validation.product.unitPrice.notNull}")
        @DecimalMin(value = "0.01", message = "{validation.product.unitPrice.decimalMin}")
        @Digits(integer = 10, fraction = 2, message = "{validation.product.unitPrice.digits}")
        @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
        private BigDecimal unitPrice;

        @NotBlank(message = "{validation.product.productCode.notBlank}")
        @Size(max = 50)
        @Column(name = "product_code", nullable = false, unique = true, length = 50)
        private String productCode;

        @NotNull(message = "{validation.product.currentStock.notNull}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.product.currentStock.decimalMin}")
        @Digits(integer = 10, fraction = 3, message = "{validation.product.currentStock.digits}")
        @Column(name = "current_stock", nullable = false, precision = 10, scale = 3)
        private BigDecimal currentStock;

        @DecimalMin(value = "0.00", message = "{validation.product.availabilityPercentage.decimalMin}")
        @DecimalMax(value = "100.00", message = "{validation.product.availabilityPercentage.decimalMax}")
        @Digits(integer = 3, fraction = 2, message = "{validation.product.availabilityPercentage.digits}")
        @Column(name = "availability_percentage", precision = 5, scale = 2)
        private BigDecimal availabilityPercentage;

        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.product.minimumStock.decimalMin}")
        @Digits(integer = 10, fraction = 3, message = "{validation.product.minimumStock.digits}")
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
