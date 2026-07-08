package com.economato.inventory.application.usecase.user;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.economato.inventory.domain.model.user.TemporaryRoleEscalation;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.TemporaryRoleEscalationRepository;

@Service
public class RoleEscalationSchedulerService {

    private final TemporaryRoleEscalationRepository escalationRepository;
    private final UserService userService;

    public RoleEscalationSchedulerService(
            TemporaryRoleEscalationRepository escalationRepository,
            UserService userService) {
        this.escalationRepository = escalationRepository;
        this.userService = userService;
    }

    @Scheduled(cron = "0 * * * * *") // Se comprueba cada minuto
    public void expireEscalations() {
        LocalDateTime now = LocalDateTime.now();
        List<TemporaryRoleEscalation> expired = escalationRepository.findExpiredWithUser(now);

        List<Integer> userIds = expired.stream()
                .map(TemporaryRoleEscalation::getUser)
                .filter(java.util.Objects::nonNull)
                .map(user -> user.getId())
                .toList();

        userService.deescalateRoles(userIds, "AUTO_EXPIRED");
    }
}
