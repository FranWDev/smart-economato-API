package com.economato.user.application.port.in;

import com.economato.user.application.dto.response.UserActivityLogResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserActivityUseCase {
    Page<UserActivityLogResponseDTO> getActivityByUserId(Integer userId, String requesterUsername, Pageable pageable);
    Page<UserActivityLogResponseDTO> getAllActivity(Pageable pageable);
    Page<UserActivityLogResponseDTO> getMyStudentsActivity(String chefUsername, Pageable pageable);
}
