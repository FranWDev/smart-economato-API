package com.economatom.inventory.repository;

import com.economatom.inventory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByName(String username);
    
    List<User> findByRole(String role);
    
    long countByRole(String role);
    
    List<User> findByNameContainingIgnoreCase(String namePart);

    boolean existsByEmail(String email);

    boolean existsById(Integer id);
    
}
