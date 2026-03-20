package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.economato.inventory.domain.model.TemporaryRoleEscalation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemporaryRoleEscalationRepository extends JpaRepository<TemporaryRoleEscalation, Integer> {

    Optional<TemporaryRoleEscalation> findByUserId(Integer userId);

    void deleteByUserId(Integer userId);

    @Query("SELECT t FROM TemporaryRoleEscalation t JOIN FETCH t.user WHERE t.expirationTime < :now")
    List<TemporaryRoleEscalation> findExpiredWithUser(@Param("now") LocalDateTime now);
}
