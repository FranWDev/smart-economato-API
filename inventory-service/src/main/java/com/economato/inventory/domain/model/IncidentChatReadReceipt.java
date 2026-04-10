package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "incident_chat_read_receipt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_incident_chat_read_receipt_incident_user", columnNames = {"incident_id", "user_id"})
})
public class IncidentChatReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_chat_read_receipt_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_read_receipt_incident"))
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_read_receipt_user"))
    private User user;

    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;
}
