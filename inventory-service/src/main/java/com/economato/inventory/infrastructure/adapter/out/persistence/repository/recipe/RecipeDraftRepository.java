package com.economato.inventory.infrastructure.adapter.out.persistence.repository.recipe;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.recipe.RecipeDraft;
import com.economato.inventory.domain.model.recipe.RecipeDraftStatus;

public interface RecipeDraftRepository extends JpaRepository<RecipeDraft, Integer> {

    @EntityGraph(attributePaths = {"createdBy", "reviewedBy"})
    Page<RecipeDraft> findByStatus(RecipeDraftStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "reviewedBy"})
    Page<RecipeDraft> findByCreatedById(Integer userId, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "reviewedBy"})
    Page<RecipeDraft> findByCreatedByIdAndStatus(Integer userId, RecipeDraftStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "reviewedBy"})
    Optional<RecipeDraft> findById(Integer id);
}