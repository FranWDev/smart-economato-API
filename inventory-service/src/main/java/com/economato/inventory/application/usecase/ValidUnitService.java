package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.ValidUnit;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ValidUnitRepository;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ValidUnitService {

    private final ValidUnitRepository validUnitRepository;
    private final ProductRepository productRepository;
    private final I18nService i18nService;

    @Transactional(readOnly = true)
    public List<ValidUnit> getAll() {
        return validUnitRepository.findAllByOrderByCategoryAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<ValidUnit> getActive() {
        return validUnitRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
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
