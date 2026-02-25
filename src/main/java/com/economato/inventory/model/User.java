package com.economato.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
                @Index(name = "idx_user_user", columnList = "\"user\"", unique = true),
                @Index(name = "idx_user_role", columnList = "role")
}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_user", columnNames = "\"user\"")
})
public class User {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "user_id")
        private Integer id;

        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        @Column(name = "name", nullable = false, length = 100)
        private String name;

        @NotBlank(message = "El usuario no puede estar vacío")
        @Size(max = 100)
        @Column(name = "\"user\"", nullable = false, unique = true, length = 100)
        private String user;

        @Column(name = "is_first_login", nullable = false)
        private boolean isFirstLogin = true;

        @Column(name = "is_hidden", nullable = false)
        private boolean isHidden = false;

        @NotBlank(message = "La contraseña no puede estar vacía")
        @Column(name = "password", nullable = false, length = 255)
        private String password;

        @NotNull(message = "El rol no puede estar vacío")
        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false, length = 20)
        private Role role;

        @JsonIgnore
        @OneToMany(mappedBy = "users")
        private List<Order> orders;

        @JsonIgnore
        @OneToMany(mappedBy = "users")
        private List<InventoryAudit> inventoryMovements;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "teacher_id")
        private User teacher;
}
