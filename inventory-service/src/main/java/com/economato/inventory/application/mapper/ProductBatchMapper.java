package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.ProductBatchResponseDTO;
import com.economato.inventory.domain.model.ProductBatch;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ProductBatchMapper {

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "expirationDate", target = "expired", qualifiedByName = "mapExpired")
    @Mapping(source = "expirationDate", target = "daysUntilExpiration", qualifiedByName = "mapDaysUntilExpiration")
    ProductBatchResponseDTO toResponseDTO(ProductBatch batch);

    @Named("mapExpired")
    default boolean mapExpired(LocalDate expirationDate) {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }

    @Named("mapDaysUntilExpiration")
    default long mapDaysUntilExpiration(LocalDate expirationDate) {
        if (expirationDate == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
        return Math.max(days, 0L);
    }
}
