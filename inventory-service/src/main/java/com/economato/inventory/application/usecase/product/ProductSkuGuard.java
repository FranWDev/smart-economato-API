package com.economato.inventory.application.usecase.product;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.economato.inventory.application.dto.product.request.ProductRequestDTO;
import com.economato.inventory.domain.model.product.ValidUnit;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.SupplierRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@Component
public class ProductSkuGuard {

    private static final Set<String> VALID_UNITS = Set.of(
            // Peso
            "KG", "G", "MG", "ONZA", "LIBRA",
            // Volumen
            "L", "ML", "CL", "DL", "GARRAFA",
            // Medidas de cocina
            "CUCHARADA", "CUCHARADITA", "TAZA", "PIZCA", "VASO",
            // Unidades discretas
            "UNIDAD", "UND", "UDS", "PIEZA", "DOCENA",
            // Envases/Empaquetado
            "BOTE", "LATA", "PAQUETE", "SOBRE", "BOLSA", "CAJA", "SACO", "BANDEJA", "TUBO",
            // Formas específicas de cocina
            "MANOJO", "HOJA", "LONCHA", "DIENTE", "RAMA", "FILETE", "RODAJA", "REBANADA"
    );

    private final ProductRepository repository;
    private final SupplierRepository supplierRepository;
    private final ValidUnitService validUnitService;
    private final I18nService i18nService;

    @Autowired
    public ProductSkuGuard(ProductRepository repository,
                           SupplierRepository supplierRepository,
                           @Autowired(required = false) ValidUnitService validUnitService,
                           I18nService i18nService) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.validUnitService = validUnitService;
        this.i18nService = i18nService;
    }

    public void validateProductCodeUniqueness(String productCode) {
        if (productCode != null && repository.existsByProductCode(productCode)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_CODE_ALREADY_EXISTS));
        }
    }

    public void validateNameUniqueness(String name) {
        if (repository.existsByName(name)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_PRODUCT_ALREADY_EXISTS));
        }
    }

    public void validateProductData(ProductRequestDTO requestDTO) {
        if (!isValidUnit(requestDTO.getUnit())) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_PRODUCT_INVALID_UNIT));
        }

        if (requestDTO.getSupplierId() != null) {
            if (!supplierRepository.existsById(requestDTO.getSupplierId())) {
                throw new InvalidOperationException(
                        i18nService.getMessage(MessageKey.ERROR_PRODUCT_SUPPLIER_NOT_FOUND));
            }
        }
    }

    private boolean isValidUnit(String unit) {
        if (unit == null) {
            return false;
        }
        String normalized = unit.toUpperCase();
        if (validUnitService == null) {
            return VALID_UNITS.contains(normalized);
        }
        try {
            List<ValidUnit> activeUnits = validUnitService.getActive();
            if (activeUnits == null) {
                activeUnits = Collections.emptyList();
            }
            if (activeUnits.isEmpty()) {
                return VALID_UNITS.contains(normalized);
            }
            return activeUnits.stream()
                    .map(v -> v.getCode().toUpperCase())
                    .anyMatch(normalized::equals);
        } catch (Exception ignored) {
            return VALID_UNITS.contains(normalized);
        }
    }
}
