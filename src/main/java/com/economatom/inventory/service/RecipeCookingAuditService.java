package com.economatom.inventory.service;

import com.economatom.inventory.dto.response.RecipeCookingAuditResponseDTO;
import com.economatom.inventory.mapper.RecipeCookingAuditMapper;
import com.economatom.inventory.repository.RecipeCookingAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecipeCookingAuditService {

    private final RecipeCookingAuditRepository repository;
    private final RecipeCookingAuditMapper mapper;

    public RecipeCookingAuditService(
            RecipeCookingAuditRepository repository,
            RecipeCookingAuditMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<RecipeCookingAuditResponseDTO> findAll(Pageable pageable) {
        Page<RecipeCookingAuditResponseDTO> page = repository.findAllOrderByDateDesc(pageable)
                .map(mapper::toResponseDTO);
        return page.getContent();
    }

    public List<RecipeCookingAuditResponseDTO> findByRecipeId(Integer recipeId) {
        return repository.findByRecipeId(recipeId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByUserId(Integer userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<RecipeCookingAuditResponseDTO> findByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return repository.findByDateRange(startDate, endDate).stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
