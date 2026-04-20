package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.TemporaryRoleEscalation;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.TemporaryRoleEscalationRepository;

@ExtendWith(MockitoExtension.class)
class RoleEscalationSchedulerServiceTest {

    @Mock
    private TemporaryRoleEscalationRepository escalationRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private RoleEscalationSchedulerService schedulerService;

    @Test
    void expireEscalations_WhenExpiredEscalationsExist_ShouldDeescalateWithAutoExpiredReason() {
        User firstUser = new User();
        firstUser.setId(10);
        firstUser.setRole(Role.ELEVATED);

        User secondUser = new User();
        secondUser.setId(11);
        secondUser.setRole(Role.ELEVATED);

        TemporaryRoleEscalation firstEscalation = new TemporaryRoleEscalation();
        firstEscalation.setUser(firstUser);
        firstEscalation.setExpirationTime(LocalDateTime.now().minusMinutes(5));

        TemporaryRoleEscalation secondEscalation = new TemporaryRoleEscalation();
        secondEscalation.setUser(secondUser);
        secondEscalation.setExpirationTime(LocalDateTime.now().minusMinutes(1));

        when(escalationRepository.findExpiredWithUser(any(LocalDateTime.class)))
                .thenReturn(List.of(firstEscalation, secondEscalation));

        schedulerService.expireEscalations();

        verify(userService).deescalateRoles(List.of(10, 11), "AUTO_EXPIRED");
    }
}
