package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_api_key", indexes = {
        @Index(name = "idx_api_key_user_provider", columnList = "user_id, provider, active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_provider", columnNames = {"user_id", "provider"})
})
@EntityListeners(AuditingEntityListener.class)
public class UserApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_api_key_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_api_key_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "encrypted_key", nullable = false, length = 512)
    private String encryptedKey;

    @Column(name = "key_hint", nullable = false, length = 8)
    private String keyHint;

    @Column(name = "encryption_key_version", nullable = false)
    @Builder.Default
    private int encryptionKeyVersion = 1;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
