package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.domain.model.UserActivityLog;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findByUserIdOrderByTimestampDesc(Integer userId, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findByUserIdInOrderByTimestampDesc(List<Integer> userIds, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserActivityLog u WHERE u.timestamp < :threshold")
    int deleteByTimestampBefore(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserActivityLog u WHERE u.timestamp BETWEEN :from AND :to")
    int deleteByTimestampBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
