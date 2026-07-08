package com.economato.inventory.infrastructure.adapter.out.persistence.repository.ai;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.user.MessageRole;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByChatIdOrderByCreatedAtAsc(Long chatId);

    Page<AiChatMessage> findByChatId(Long chatId, Pageable pageable);

    List<AiChatMessage> findByChatIdAndRoleOrderByCreatedAtDesc(Long chatId, MessageRole role);

    List<AiChatMessage> findByChatIdAndToolNameIsNotNullOrderByCreatedAtAsc(Long chatId);

    long countByChatId(Long chatId);
}
