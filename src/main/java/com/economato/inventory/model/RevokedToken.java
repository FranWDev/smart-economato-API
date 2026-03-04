package com.economato.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens", indexes = {
    @Index(name = "idx_revoked_token_token", columnList = "token", unique = true),
    @Index(name = "idx_revoked_token_expiration", columnList = "expiration_date")
})
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 1024)
    private String token;

    @Column(name = "revocation_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date revocationDate;

    @Column(name = "expiration_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationDate;

    public RevokedToken(String token, Date expirationDate) {
        this.token = token;
        this.revocationDate = new Date();
        this.expirationDate = expirationDate;
    }
}
