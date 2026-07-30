package com.economato.user.application.port.in;

import com.economato.user.application.dto.request.BatchTeacherAssignmentRequestDTO;
import com.economato.user.application.dto.request.ChangePasswordRequestDTO;
import com.economato.user.application.dto.request.RoleEscalationRequestDTO;
import com.economato.user.application.dto.request.UserRequestDTO;
import com.economato.user.application.dto.response.BatchTeacherAssignmentResponseDTO;
import com.economato.user.application.dto.response.UserResponseDTO;
import com.economato.user.application.dto.response.UserStatsResponseDTO;
import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

public interface UserManagementUseCase {
    Page<UserResponseDTO> findAll(Pageable pageable);
    Page<UserResponseDTO> searchVisibleUsers(String term, Pageable pageable);
    Page<UserResponseDTO> searchVisibleTeachers(String term, Pageable pageable);
    Optional<UserResponseDTO> findById(Integer id);
    User findByUsername(String username);
    UserResponseDTO findCurrentUser(String username);
    UserResponseDTO findCurrentUserWithToken(String username, String token);
    UserResponseDTO save(UserRequestDTO requestDTO);
    Optional<UserResponseDTO> update(Integer id, UserRequestDTO requestDTO);
    void deleteById(Integer id);
    void updateFirstLoginStatus(Integer id, boolean status, boolean isAdmin);
    void updateFirstLoginStatusByActor(Integer id, boolean status, Authentication authentication);
    void changePassword(Integer id, ChangePasswordRequestDTO request);
    void changePasswordByActor(Integer id, ChangePasswordRequestDTO request, Authentication authentication);
    List<UserResponseDTO> findByRole(Role role);
    Page<UserResponseDTO> findHiddenUsers(Pageable pageable);
    void toggleUserHiddenStatus(Integer id, boolean hidden);
    void assignTeacher(Integer userId, Integer teacherId);
    BatchTeacherAssignmentResponseDTO assignTeacherBatch(BatchTeacherAssignmentRequestDTO request);
    List<UserResponseDTO> getMyStudents(String username);
    List<UserResponseDTO> getStudentsByTeacherId(Integer teacherId);
    UserStatsResponseDTO getUserStats();
    void escalateRole(Integer userId, RoleEscalationRequestDTO request);
    void deescalateRole(Integer userId);
    void deescalateRole(Integer userId, String reason);
}
