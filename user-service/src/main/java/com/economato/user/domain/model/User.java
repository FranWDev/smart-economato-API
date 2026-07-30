package com.economato.user.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotBlank(message = "{validation.user.name.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.user.name.size}")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "{validation.user.user.notBlank}")
    @Size(max = 100)
    @Column(name = "\"user\"", nullable = false, unique = true, length = 100)
    private String user;

    @Column(name = "is_first_login", nullable = false)
    private boolean isFirstLogin = true;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden = false;

    @NotBlank(message = "{validation.user.password.notBlank}")
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @NotNull(message = "{validation.user.role.notNull}")
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;
}
