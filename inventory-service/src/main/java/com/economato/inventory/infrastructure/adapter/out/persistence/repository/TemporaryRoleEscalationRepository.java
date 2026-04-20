package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.economato.inventory.domain.model.TemporaryRoleEscalation;

@Repository
public interface TemporaryRoleEscalationRepository extends JpaRepository<TemporaryRoleEscalation, Integer> {

    Optional<TemporaryRoleEscalation> findByUserId(Integer userId);

    void deleteByUserId(Integer userId);

    void deleteByUserIdIn(Collection<Integer> userIds);

    @Query("SELECT t FROM TemporaryRoleEscalation t JOIN FETCH t.user WHERE t.expirationTime < :now")
    List<TemporaryRoleEscalation> findExpiredWithUser(@Param("now") LocalDateTime now);
}
