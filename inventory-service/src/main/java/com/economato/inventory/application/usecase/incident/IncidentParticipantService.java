package com.economato.inventory.application.usecase.incident;

import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentParticipantService {

    private final UserRepository userRepository;

    public boolean isParticipant(Incident incident, User user) {
        if (incident == null || user == null) {
            return false;
        }

        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        if (incident.getCreatedBy() != null && user.getId().equals(incident.getCreatedBy().getId())) {
            return true;
        }

        return incident.getRelatedTeacher() != null && user.getId().equals(incident.getRelatedTeacher().getId());
    }

    public Set<Integer> allowedAuditUserIds(User currentUser) {
        Set<Integer> userIds = new LinkedHashSet<>();
        if (currentUser == null) {
            return userIds;
        }

        userIds.add(currentUser.getId());

        if (currentUser.getRole() == Role.CHEF) {
            userRepository.findByTeacherId(currentUser.getId())
                    .stream()
                    .map(User::getId)
                    .forEach(userIds::add);
        }

        return userIds;
    }
}
