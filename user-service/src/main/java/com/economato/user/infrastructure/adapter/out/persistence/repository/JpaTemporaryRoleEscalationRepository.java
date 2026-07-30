package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.domain.model.TemporaryRoleEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JpaTemporaryRoleEscalationRepository extends JpaRepository<TemporaryRoleEscalation, Integer> {
    Optional<TemporaryRoleEscalation> findByUserId(Integer userId);
    void deleteByUserId(Integer userId);
    void deleteByUserIdIn(Collection<Integer> userIds);

    @Query("SELECT t FROM TemporaryRoleEscalation t JOIN FETCH t.user WHERE t.expirationTime < :now")
    List<TemporaryRoleEscalation> findExpiredWithUser(@Param("now") LocalDateTime now);
}
