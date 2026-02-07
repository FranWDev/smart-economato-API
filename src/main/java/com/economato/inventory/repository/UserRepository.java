package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByName(String username);
    
    List<User> findByRole(Role role);
    
    long countByRole(Role role);
    
    List<User> findByNameContainingIgnoreCase(String namePart);

    boolean existsByEmail(String email);

    boolean existsById(Integer id);
    
}
