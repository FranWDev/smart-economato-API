package com.economato.inventory.application.usecase.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.economato.inventory.application.dto.shared.request.ChangePasswordRequestDTO;
import com.economato.inventory.application.dto.user.request.RoleEscalationRequestDTO;
import com.economato.inventory.application.usecase.shared.SystemConfigService;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@Component
public class UserAccessPolicy {

    private final UserRepository repository;
    private final SystemConfigService systemConfigService;
    private final I18nService i18nService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserAccessPolicy(UserRepository repository,
                            @Autowired(required = false) SystemConfigService systemConfigService,
                            I18nService i18nService,
                            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.systemConfigService = systemConfigService;
        this.i18nService = i18nService;
        this.passwordEncoder = passwordEncoder;
    }

    public void validatePasswordLength(String password) {
        int minLength = resolveMinPasswordLength();
        if (password == null || password.length() < minLength) {
            throw new InvalidOperationException(i18nService.getMessage(
                    MessageKey.ERROR_PASSWORD_TOO_SHORT,
                    new Object[] { minLength }));
        }
    }

    public void validateTeacherAssignment(Role userRole, Integer teacherId) {
        if (teacherId != null) {
            if (Role.CHEF.equals(userRole) || Role.ADMIN.equals(userRole)) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_ADMIN_CANNOT_HAVE_TEACHER));
            }
            User teacher = repository.findById(teacherId)
                    .orElseThrow(
                            () -> new InvalidOperationException(i18nService
                                     .getMessage(MessageKey.ERROR_USER_TEACHER_NOT_FOUND, new Object[] { teacherId })));
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_TEACHER_MUST_BE_ADMIN));
            }
        }
    }

    public void validateAdminDeletion(User user) {
        if (Role.ADMIN.equals(user.getRole())) {
            long adminCount = repository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_DELETE_LAST_ADMIN));
            }
        }
    }

    public void validateAdminHiding(User user, boolean hidden) {
        if (hidden && Role.ADMIN.equals(user.getRole())) {
            long visibleAdmins = repository.countByRoleAndIsHiddenFalse(Role.ADMIN);
            if (visibleAdmins <= 1) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_HIDE_LAST_ADMIN));
            }
        }
    }

    public void validateFirstLoginReactivation(User user, boolean status, boolean isAdmin) {
        if (!isAdmin && status && !user.isFirstLogin()) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_USER_FIRST_LOGIN_REACTIVATE_DENIED));
        }
    }

    public void validatePasswordChange(User user, ChangePasswordRequestDTO request, boolean isAdmin) {
        if (!isAdmin && !user.isFirstLogin()) {
            if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_REQUIRE_CURRENT_PASSWORD));
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_USER_INVALID_CURRENT_PASSWORD));
            }
        }
    }

    public void validateRoleEscalation(User user, RoleEscalationRequestDTO request) {
        if (Role.ELEVATED.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_ALREADY_ELEVATED));
        }
        if (Role.ADMIN.equals(user.getRole())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_USER_CANNOT_ESCALATE_ADMIN));
        }

        int maxEscalationMinutes = resolveMaxEscalationMinutes();
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > maxEscalationMinutes) {
            throw new InvalidOperationException(i18nService.getMessage(
                    MessageKey.ERROR_ESCALATION_DURATION_EXCEEDS_MAX,
                    new Object[] { maxEscalationMinutes }));
        }
    }

    private int resolveMinPasswordLength() {
        if (systemConfigService == null) {
            return 6;
        }
        try {
            return systemConfigService.getMinPasswordLength();
        } catch (Exception ignored) {
            return 6;
        }
    }

    private int resolveMaxEscalationMinutes() {
        if (systemConfigService == null) {
            return 1440;
        }
        try {
            return systemConfigService.getMaxEscalationMinutes();
        } catch (Exception ignored) {
            return 1440;
        }
    }
}
