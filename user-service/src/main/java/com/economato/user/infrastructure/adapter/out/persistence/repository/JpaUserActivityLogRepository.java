package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.domain.model.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JpaUserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
    Page<UserActivityLog> findByUserIdOrderByTimestampDesc(Integer userId, Pageable pageable);
    Page<UserActivityLog> findByUserIdInOrderByTimestampDesc(List<Integer> userIds, Pageable pageable);
    Page<UserActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM UserActivityLog u WHERE u.timestamp < :threshold")
    int deleteByTimestampBefore(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM UserActivityLog u WHERE u.timestamp BETWEEN :from AND :to")
    int deleteByTimestampBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
