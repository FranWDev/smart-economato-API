package com.economato.user.application.service;

import com.economato.user.application.dto.response.UserActivityLogResponseDTO;
import com.economato.user.application.port.in.UserActivityUseCase;
import com.economato.user.domain.model.UserActivityLog;
import com.economato.user.infrastructure.adapter.out.persistence.repository.JpaUserActivityLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserActivityLogService implements UserActivityUseCase {

    private final JpaUserActivityLogRepository activityLogRepository;

    public UserActivityLogService(JpaUserActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    public Page<UserActivityLogResponseDTO> getActivityByUserId(Integer userId, String requesterUsername, Pageable pageable) {
        return activityLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable).map(this::toResponseDTO);
    }

    @Override
    public Page<UserActivityLogResponseDTO> getAllActivity(Pageable pageable) {
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable).map(this::toResponseDTO);
    }

    @Override
    public Page<UserActivityLogResponseDTO> getMyStudentsActivity(String chefUsername, Pageable pageable) {
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable).map(this::toResponseDTO);
    }

    private UserActivityLogResponseDTO toResponseDTO(UserActivityLog log) {
        if (log == null) return null;
        return UserActivityLogResponseDTO.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .username(log.getUser() != null ? log.getUser().getUser() : null)
                .displayName(log.getUser() != null ? log.getUser().getName() : null)
                .action(log.getAction())
                .screen(log.getScreen())
                .screenContext(log.getScreenContext())
                .sessionId(log.getSessionId())
                .timestamp(log.getTimestamp())
                .build();
    }
}
