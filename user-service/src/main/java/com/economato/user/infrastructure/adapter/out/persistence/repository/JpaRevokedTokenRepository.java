package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.domain.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

public interface JpaRevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    boolean existsByToken(String token);
    Optional<RevokedToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken rt WHERE rt.expirationDate < :currentDate")
    int deleteExpiredTokens(@Param("currentDate") Date currentDate);
}
