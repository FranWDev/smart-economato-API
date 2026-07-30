package com.economato.user.application.port.out;

import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findById(Integer id);
    Optional<User> findByName(String name);
    Optional<User> findByUser(String user);
    Page<User> findAll(Pageable pageable);
    List<User> findByRole(Role role);
    Page<User> findByRoleIn(List<Role> roles, Pageable pageable);
    User save(User user);
    void deleteById(Integer id);
    boolean existsByUser(String user);
    List<User> findByTeacherId(Integer teacherId);
    Page<User> findByIsHidden(boolean isHidden, Pageable pageable);
    long countByRole(Role role);
    long countByIsHidden(boolean isHidden);
}
