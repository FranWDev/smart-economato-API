package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.RecipeDraft;
import com.economato.inventory.domain.model.RecipeDraftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeDraftRepository extends JpaRepository<RecipeDraft, Integer> {

    Page<RecipeDraft> findByStatus(RecipeDraftStatus status, Pageable pageable);

    Page<RecipeDraft> findByCreatedById(Integer userId, Pageable pageable);

    Page<RecipeDraft> findByCreatedByIdAndStatus(Integer userId, RecipeDraftStatus status, Pageable pageable);
}