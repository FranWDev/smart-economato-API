package com.economato.inventory.application.usecase;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.economato.inventory.infrastructure.aspect.annotation.RealtimeSync;

import com.economato.inventory.application.dto.request.AllergenRequestDTO;
import com.economato.inventory.application.dto.response.AllergenResponseDTO;
import com.economato.inventory.application.mapper.AllergenMapper;
import com.economato.inventory.domain.model.Allergen;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.AllergenRepository;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AllergenService {

    private final AllergenRepository repository;
    private final AllergenMapper allergenMapper;

    public AllergenService(AllergenRepository repository, AllergenMapper allergenMapper) {
        this.repository = repository;
        this.allergenMapper = allergenMapper;
    }

    @Cacheable(value = "allergens_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<AllergenResponseDTO> findAll(Pageable pageable) {
        return repository.findAllProjectedBy(pageable)
                .map(allergenMapper::toResponseDTO);
    }

    @Cacheable(value = "allergen", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<AllergenResponseDTO> findById(Integer id) {
        return repository.findProjectedById(id)
                .map(allergenMapper::toResponseDTO);
    }

    @CacheEvict(value = { "allergens_page", "allergen" }, allEntries = true)
    @RealtimeSync(entityType = "allergen", action = "CREATE", idFromArg = -2,
            affectedDomains = {"recipe"})
    public AllergenResponseDTO save(AllergenRequestDTO requestDTO) {
        Allergen allergen = allergenMapper.toEntity(requestDTO);
        return allergenMapper.toResponseDTO(repository.save(allergen));
    }

    @Caching(evict = {
            @CacheEvict(value = "allergen", key = "#id"),
            @CacheEvict(value = "allergens_page", allEntries = true)
    })
    @RealtimeSync(entityType = "allergen", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"recipe"})
    public Optional<AllergenResponseDTO> update(Integer id, AllergenRequestDTO requestDTO) {
        return repository.findById(id)
                .map(existing -> {
                    allergenMapper.updateEntity(requestDTO, existing);
                    return allergenMapper.toResponseDTO(repository.save(existing));
                });
    }

    @CacheEvict(value = { "allergens_page", "allergen" }, allEntries = true)
    @RealtimeSync(entityType = "allergen", action = "DELETE", idFromArg = 0,
            affectedDomains = {"recipe"})
    public void deleteById(Integer id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
        }
    }

    @Cacheable(value = "allergen", key = "'name:' + #namePart.toLowerCase()", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<AllergenResponseDTO> findByName(String namePart) {
        return repository.findProjectedByNameIgnoreCase(namePart)
                .map(allergenMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AllergenResponseDTO> findByNameContaining(String namePart) {
        return repository.findProjectedByNameContainingIgnoreCase(namePart).stream()
                .map(allergenMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
