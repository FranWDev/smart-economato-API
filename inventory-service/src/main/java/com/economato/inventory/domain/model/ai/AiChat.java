package com.economato.inventory.domain.model.ai;
import com.economato.inventory.domain.model.user.User;

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
@Table(name = "ai_chat", indexes = {
        @Index(name = "idx_ai_chat_user_status", columnList = "user_id, status, last_message_at DESC"),
        @Index(name = "idx_ai_chat_user_provider", columnList = "user_id, active_provider")
})
@EntityListeners(AuditingEntityListener.class)
public class AiChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_chat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ai_chat_user"))
    private User user;

    @Column(name = "title", length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AiChatStatus status = AiChatStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_provider", nullable = false, length = 20)
    private AiProvider activeProvider;

    @Column(name = "user_language", nullable = false, length = 10)
    @Builder.Default
    private String userLanguage = "es";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private int messageCount = 0;

    @Column(name = "total_tokens_consumed", nullable = false)
    @Builder.Default
    private long totalTokensConsumed = 0L;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
