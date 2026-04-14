package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.AiChat;
import com.economato.inventory.domain.model.AiChatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiChatRepository extends JpaRepository<AiChat, Long> {

    List<AiChat> findByUserIdAndStatusOrderByLastMessageAtDesc(Integer userId, AiChatStatus status);

    Optional<AiChat> findByIdAndUserId(Long id, Integer userId);

    long countByUserIdAndStatus(Integer userId, AiChatStatus status);
}
