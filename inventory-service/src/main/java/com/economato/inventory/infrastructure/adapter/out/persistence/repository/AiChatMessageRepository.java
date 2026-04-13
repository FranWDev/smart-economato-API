package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByChatIdOrderByCreatedAtAsc(Long chatId);

    List<AiChatMessage> findByChatIdAndRoleOrderByCreatedAtDesc(Long chatId, MessageRole role);

    List<AiChatMessage> findByChatIdAndToolNameIsNotNullOrderByCreatedAtAsc(Long chatId);

    long countByChatId(Long chatId);
}
