package com.economato.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.dto.projection.UserProjection;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByName(String username);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    List<User> findByNameContainingIgnoreCase(String namePart);

    boolean existsByUser(String user);

    boolean existsById(Integer id);

    // --- Proyecciones ---

    Page<UserProjection> findAllProjectedBy(Pageable pageable);

    Optional<UserProjection> findProjectedById(Integer id);

    List<UserProjection> findProjectedByRole(Role role);
}
