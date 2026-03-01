package com.economato.inventory.service;

import com.economato.inventory.model.TemporaryRoleEscalation;
import com.economato.inventory.repository.TemporaryRoleEscalationRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    @Scheduled(cron = "0 * * * * *") // Run every minute
    public void expireEscalations() {
        List<TemporaryRoleEscalation> activeEscalations = escalationRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (TemporaryRoleEscalation escalation : activeEscalations) {
            if (escalation.getExpirationTime().isBefore(now)) {
                userService.deescalateRole(escalation.getUser().getId());
            }
        }
    }
}
