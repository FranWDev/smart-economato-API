package com.economato.inventory.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.RestPage;
import com.economato.inventory.application.dto.response.UserActivityLogResponseDTO;
import com.economato.inventory.application.mapper.UserActivityLogMapper;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserActivityLogRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityLogService {

    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;
    private final UserActivityLogMapper userActivityLogMapper;

    @Transactional(readOnly = true)
    public Page<UserActivityLogResponseDTO> getActivityByUserId(Integer userId, String requesterUsername, Pageable pageable) {
        User requester = userRepository.findByName(requesterUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + requesterUsername));

        Pageable normalizedPageable = ensureTimestampDesc(pageable);

        if (Role.ADMIN.equals(requester.getRole())) {
            return mapPage(userActivityLogRepository.findByUserIdOrderByTimestampDesc(userId, normalizedPageable));
        }

        if (!Role.CHEF.equals(requester.getRole())) {
            throw new AccessDeniedException("Forbidden");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        if (target.getTeacher() == null || !requester.getId().equals(target.getTeacher().getId())) {
            throw new AccessDeniedException("Forbidden");
        }

        return mapPage(userActivityLogRepository.findByUserIdOrderByTimestampDesc(userId, normalizedPageable));
    }

    @Transactional(readOnly = true)
    public Page<UserActivityLogResponseDTO> getAllActivity(Pageable pageable) {
        Pageable normalizedPageable = ensureTimestampDesc(pageable);
        return mapPage(userActivityLogRepository.findAllByOrderByTimestampDesc(normalizedPageable));
    }

    @Transactional(readOnly = true)
    public Page<UserActivityLogResponseDTO> getMyStudentsActivity(String chefUsername, Pageable pageable) {
        User chef = userRepository.findByName(chefUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + chefUsername));

        if (!Role.CHEF.equals(chef.getRole())) {
            throw new AccessDeniedException("Forbidden");
        }

        Pageable normalizedPageable = ensureTimestampDesc(pageable);
        List<Integer> studentIds = userRepository.findByTeacherId(chef.getId()).stream()
                .map(User::getId)
                .toList();

        if (studentIds.isEmpty()) {
            return new RestPage<>(List.of(), normalizedPageable, 0);
        }

        return mapPage(userActivityLogRepository.findByUserIdInOrderByTimestampDesc(studentIds, normalizedPageable));
    }

    private Page<UserActivityLogResponseDTO> mapPage(Page<com.economato.inventory.domain.model.UserActivityLog> page) {
        Page<UserActivityLogResponseDTO> mapped = page.map(userActivityLogMapper::toResponseDTO);
        return new RestPage<>(mapped.getContent(), mapped.getPageable(), mapped.getTotalElements());
    }

    private Pageable ensureTimestampDesc(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        }

        if (pageable.getSort().isSorted()) {
            return pageable;
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "timestamp"));
    }
}
