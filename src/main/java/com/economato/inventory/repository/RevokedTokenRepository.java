package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.model.RevokedToken;

import java.util.Date;
import java.util.Optional;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * Check if a token has been revoked
     */
    boolean existsByToken(String token);

    /**
     * Find a revoked token by its value
     */
    Optional<RevokedToken> findByToken(String token);

    /**
     * Delete all expired revoked tokens (cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken rt WHERE rt.expirationDate < :currentDate")
    int deleteExpiredTokens(@Param("currentDate") Date currentDate);
}
