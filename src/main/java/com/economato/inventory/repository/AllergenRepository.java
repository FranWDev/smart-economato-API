package com.economato.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.model.Allergen;

import java.util.List;

public interface AllergenRepository extends JpaRepository<Allergen, Integer> {

    List<Allergen> findByNameContainingIgnoreCase(String namePart);
    
}
