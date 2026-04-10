package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.economato.inventory.domain.model.UserActivityLog;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findByUserIdOrderByTimestampDesc(Integer userId, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findByUserIdInOrderByTimestampDesc(List<Integer> userIds, Pageable pageable);

    @EntityGraph(attributePaths = { "user" })
    Page<UserActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
