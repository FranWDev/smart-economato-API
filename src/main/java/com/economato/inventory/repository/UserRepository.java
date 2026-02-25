package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.dto.projection.UserProjection;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByName(String username);

    Optional<User> findByUser(String user);

    Optional<User> findByNameAndIsHiddenFalse(String username);

    Optional<User> findByUserAndIsHiddenFalse(String user);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    List<User> findByNameContainingIgnoreCase(String namePart);

    List<User> findByNameContainingIgnoreCaseAndIsHiddenFalse(String namePart);

    boolean existsByUser(String user);

    boolean existsById(Integer id);

    @Query("SELECT u.role as role, COUNT(u) as count FROM User u GROUP BY u.role")
    List<com.economato.inventory.dto.projection.RoleCountProjection> countUsersByRole();

    // --- Proyecciones ---

    Page<UserProjection> findAllProjectedBy(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isHidden = false")
    Page<UserProjection> findByIsHiddenFalse(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isHidden = true")
    Page<UserProjection> findByIsHiddenTrue(Pageable pageable);

    Optional<UserProjection> findProjectedById(Integer id);

    List<UserProjection> findProjectedByRole(Role role);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isHidden = false")
    List<UserProjection> findProjectedByRoleAndIsHiddenFalse(@Param("role") Role role);

    @Query("SELECT u FROM User u WHERE u.teacher.id = :teacherId AND u.isHidden = false")
    List<UserProjection> findProjectedByTeacherIdAndIsHiddenFalse(@Param("teacherId") Integer teacherId);
}
