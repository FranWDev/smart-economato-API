package com.economato.user.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
