package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.domain.model.StockWeeklyConsumptionHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockWeeklyConsumptionHistoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unit", source = "product.unit")
    WeeklyConsumptionResponseDTO toDTO(StockWeeklyConsumptionHistory entity);
}
