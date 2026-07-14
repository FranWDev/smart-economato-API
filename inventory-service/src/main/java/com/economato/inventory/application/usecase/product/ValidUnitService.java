package com.economato.inventory.application.usecase.product;

import com.economato.inventory.domain.model.product.ValidUnit;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ValidUnitRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.economato.inventory.infrastructure.aspect.shared.annotation.RealtimeSync;

import java.util.List;
import java.util.Locale;

import com.economato.inventory.application.dto.product.response.ValidUnitResponseDTO;

@Service
@RequiredArgsConstructor
public class ValidUnitService {

    private final ValidUnitRepository validUnitRepository;
    private final ProductRepository productRepository;
    private final I18nService i18nService;

    private ValidUnitResponseDTO mapToDto(ValidUnit v) {
        return ValidUnitResponseDTO.builder()
                .id(v.getId())
                .code(v.getCode())
                .category(v.getCategory())
                .active(v.isActive())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ValidUnitResponseDTO> getAllDto() {
        return getAll().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ValidUnitResponseDTO> getActiveDto() {
        return getActive().stream().map(this::mapToDto).toList();
    }

    @Transactional
    public ValidUnitResponseDTO createDto(String code, String category) {
        return mapToDto(create(code, category));
    }

    @Transactional
    public ValidUnitResponseDTO updateDto(Integer id, String code, String category, Boolean active) {
        return mapToDto(update(id, code, category, active));
    }

    @Transactional
    public ValidUnitResponseDTO toggleActiveDto(Integer id) {
        return mapToDto(toggleActive(id));
    }

    @Transactional(readOnly = true)
    public List<ValidUnit> getAll() {
        return validUnitRepository.findAllByOrderByCategoryAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<ValidUnit> getActive() {
        return validUnitRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
    @RealtimeSync(entityType = "config", action = "CREATE", idFromArg = -2,
            affectedDomains = {"product"})
    public ValidUnit create(String code, String category) {
        String normalizedCode = normalize(code);
        if (validUnitRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_ALREADY_EXISTS));
        }
        ValidUnit unit = ValidUnit.builder()
                .code(normalizedCode)
                .category(normalize(category))
                .active(true)
                .build();
        return validUnitRepository.save(unit);
    }

    @Transactional
    @RealtimeSync(entityType = "config", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"product"})
    public ValidUnit update(Integer id, String code, String category, Boolean active) {
        ValidUnit existing = validUnitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_NOT_FOUND)));

        String normalizedCode = normalize(code);
        if (!existing.getCode().equalsIgnoreCase(normalizedCode)
                && validUnitRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_ALREADY_EXISTS));
        }

        existing.setCode(normalizedCode);
        existing.setCategory(normalize(category));

        if (active != null && existing.isActive() != active) {
            if (!active && productRepository.existsByUnitIgnoreCaseAndIsHiddenFalse(existing.getCode())) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_IN_USE));
            }
            existing.setActive(active);
        }

        return validUnitRepository.save(existing);
    }

    @Transactional
    @RealtimeSync(entityType = "config", action = "UPDATE", idFromArg = 0,
            affectedDomains = {"product"})
    public ValidUnit toggleActive(Integer id) {
        ValidUnit existing = validUnitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_NOT_FOUND)));

        boolean next = !existing.isActive();
        if (!next && productRepository.existsByUnitIgnoreCaseAndIsHiddenFalse(existing.getCode())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_VALID_UNIT_IN_USE));
        }
        existing.setActive(next);
        return validUnitRepository.save(existing);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
