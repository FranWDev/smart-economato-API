package com.economato.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "temporary_role_escalation", indexes = {
        @Index(name = "idx_escalation_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_escalation_expiration", columnList = "expiration_time")
})
public class TemporaryRoleEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

}
