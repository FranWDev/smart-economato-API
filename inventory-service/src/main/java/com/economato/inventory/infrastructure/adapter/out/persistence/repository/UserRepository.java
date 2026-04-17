package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.economato.inventory.application.dto.projection.RoleCountProjection;
import com.economato.inventory.application.dto.projection.UserProjection;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.domain.model.User;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

public interface UserRepository extends JpaRepository<User, Integer> {

    @EntityGraph(attributePaths = {"teacher"})
    Optional<User> findByName(String username);

    Optional<User> findByUser(String user);

    Optional<User> findByNameAndIsHiddenFalse(String username);

    Optional<User> findByUserAndIsHiddenFalse(String user);

    List<User> findByRole(Role role);

    List<User> findByRoleAndIsHiddenFalse(Role role);

    List<User> findByIsHiddenFalse();

    long countByRole(Role role);

    List<User> findByNameContainingIgnoreCase(String namePart);

    List<User> findByNameContainingIgnoreCaseAndIsHiddenFalse(String namePart);

    boolean existsByUser(String user);

    @Query("SELECT u.role as role, COUNT(u) as count FROM User u GROUP BY u.role")
    List<RoleCountProjection> countUsersByRole();

    long countByRoleAndIsHiddenFalse(Role role);

    Page<UserProjection> findAllProjectedBy(Pageable pageable);

    Page<UserProjection> findByIsHiddenFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.isHidden = false AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(u.user) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<UserProjection> searchVisibleByNameOrUser(@Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isHidden = false AND (:term = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(u.user) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<UserProjection> searchVisibleByRoleAndNameOrUser(@Param("role") Role role, @Param("term") String term, Pageable pageable);

    Page<UserProjection> findByIsHiddenTrue(Pageable pageable);

    Optional<UserProjection> findProjectedById(Integer id);

    List<UserProjection> findProjectedByRole(Role role);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isHidden = false")
    List<UserProjection> findProjectedByRoleAndIsHiddenFalse(@Param("role") Role role);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.role IN :roles AND u.isHidden = false")
    List<UserProjection> findProjectedByRoleInAndIsHiddenFalse(@Param("roles") List<Role> roles);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.teacher.id = :teacherId AND u.isHidden = false")
    List<UserProjection> findProjectedByTeacherIdAndIsHiddenFalse(@Param("teacherId") Integer teacherId);
    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.teacher IS NULL AND u.role IN :roles AND u.isHidden = false")
    List<UserProjection> findProjectedByTeacherIsNullAndRoleInAndIsHiddenFalse(@Param("roles") List<Role> roles);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.isHidden = :hidden WHERE u.teacher.id = :teacherId")
    int updateHiddenByTeacherId(@Param("teacherId") Integer teacherId, @Param("hidden") boolean hidden);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.teacher.id = :teacherId")
    List<User> findByTeacherId(@Param("teacherId") Integer teacherId);
}
