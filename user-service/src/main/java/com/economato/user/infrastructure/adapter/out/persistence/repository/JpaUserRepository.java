package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<User, Integer> {

    @EntityGraph(attributePaths = {"teacher"})
    Optional<User> findByName(String username);

    Optional<User> findByUser(String user);

    Optional<User> findByNameAndIsHiddenFalse(String username);

    Optional<User> findByUserAndIsHiddenFalse(String user);

    List<User> findByRole(Role role);

    List<User> findByRoleAndIsHiddenFalse(Role role);

    List<User> findByIsHiddenFalse();

    long countByRole(Role role);

    long countByIsHidden(boolean isHidden);

    long countByRoleAndIsHiddenFalse(Role role);

    boolean existsByUser(String user);

    Page<User> findByIsHiddenFalse(Pageable pageable);

    Page<User> findByIsHiddenTrue(Pageable pageable);

    Page<User> findByRoleIn(List<Role> roles, Pageable pageable);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.isHidden = false AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(u.user) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<User> searchVisibleByNameOrUser(@Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isHidden = false AND (:term = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(u.user) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<User> searchVisibleByRoleAndNameOrUser(@Param("role") Role role, @Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.teacher.id = :teacherId AND u.isHidden = false")
    List<User> findByTeacherIdAndIsHiddenFalse(@Param("teacherId") Integer teacherId);

    @EntityGraph(attributePaths = {"teacher"})
    @Query("SELECT u FROM User u WHERE u.teacher.id = :teacherId")
    List<User> findByTeacherId(@Param("teacherId") Integer teacherId);

    @Modifying
    @Query("UPDATE User u SET u.isHidden = :hidden WHERE u.teacher.id = :teacherId")
    int updateHiddenByTeacherId(@Param("teacherId") Integer teacherId, @Param("hidden") boolean hidden);
}
