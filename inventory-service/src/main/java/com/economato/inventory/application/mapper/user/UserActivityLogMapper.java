package com.economato.inventory.application.mapper.user;

import org.springframework.stereotype.Component;

import com.economato.inventory.application.dto.user.response.UserActivityLogResponseDTO;
import com.economato.inventory.domain.model.user.UserActivityLog;

@Component
public class UserActivityLogMapper {

    public UserActivityLogResponseDTO toResponseDTO(UserActivityLog entity) {
        if (entity == null) {
            return null;
        }

        return UserActivityLogResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .username(entity.getUser() != null ? entity.getUser().getUser() : null)
                .displayName(entity.getUser() != null ? entity.getUser().getName() : null)
                .action(entity.getAction())
                .screen(entity.getScreen())
                .screenContext(entity.getScreenContext())
                .sessionId(entity.getSessionId())
                .timestamp(entity.getTimestamp())
                .build();
    }
}
