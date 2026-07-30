package com.economato.user.application.service;

import com.economato.user.application.dto.request.ChangePasswordRequestDTO;
import com.economato.user.application.dto.request.RoleEscalationRequestDTO;
import com.economato.user.application.port.out.UserRepositoryPort;
import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import com.economato.user.infrastructure.config.web.I18nService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserAccessPolicy {

    private final UserRepositoryPort repository;
    private final I18nService i18nService;
    private final PasswordEncoder passwordEncoder;

    public UserAccessPolicy(UserRepositoryPort repository,
                             I18nService i18nService,
                             PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.i18nService = i18nService;
        this.passwordEncoder = passwordEncoder;
    }

    public void validatePasswordLength(String password) {
        int minLength = 6;
        if (password == null || password.length() < minLength) {
            throw new RuntimeException(i18nService.getMessage("error.password.too.short"));
        }
    }

    public void validateTeacherAssignment(Role userRole, Integer teacherId) {
        if (teacherId != null) {
            if (Role.CHEF.equals(userRole) || Role.ADMIN.equals(userRole)) {
                throw new RuntimeException(i18nService.getMessage("error.user.admin.cannot.have.teacher"));
            }
            User teacher = repository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("error.user.teacher.not.found")));
            if (!Role.CHEF.equals(teacher.getRole())) {
                throw new RuntimeException(i18nService.getMessage("error.user.teacher.must.be.chef"));
            }
        }
    }

    public void validateAdminDeletion(User user) {
        if (Role.ADMIN.equals(user.getRole())) {
            long adminCount = repository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new RuntimeException(i18nService.getMessage("error.user.delete.last.admin"));
            }
        }
    }

    public void validateAdminHiding(User user, boolean hidden) {
        if (hidden && Role.ADMIN.equals(user.getRole())) {
            long visibleAdmins = repository.countByIsHidden(false);
            if (visibleAdmins <= 1) {
                throw new RuntimeException(i18nService.getMessage("error.user.hide.last.admin"));
            }
        }
    }

    public void validateFirstLoginReactivation(User user, boolean status, boolean isAdmin) {
        if (!isAdmin && status && !user.isFirstLogin()) {
            throw new RuntimeException(i18nService.getMessage("error.user.first.login.reactivate.denied"));
        }
    }

    public void validatePasswordChange(User user, ChangePasswordRequestDTO request, boolean isAdmin) {
        if (!isAdmin && !user.isFirstLogin()) {
            if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                throw new RuntimeException(i18nService.getMessage("error.user.require.current.password"));
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new RuntimeException(i18nService.getMessage("error.user.invalid.current.password"));
            }
        }
    }

    public void validateRoleEscalation(User user, RoleEscalationRequestDTO request) {
        if (Role.ADMIN.equals(user.getRole())) {
            throw new RuntimeException(i18nService.getMessage("error.user.cannot.escalate.admin"));
        }

        int maxEscalationMinutes = 1440;
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > maxEscalationMinutes) {
            throw new RuntimeException(i18nService.getMessage("error.escalation.duration.exceeds.max"));
        }
    }
}
