package com.economato.inventory.infrastructure.adapter.out.persistence.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.domain.model.user.RevokedToken;

import java.util.Date;
import java.util.Optional;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    boolean existsByToken(String token);

    Optional<RevokedToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken rt WHERE rt.expirationDate < :currentDate")
    int deleteExpiredTokens(@Param("currentDate") Date currentDate);
}
