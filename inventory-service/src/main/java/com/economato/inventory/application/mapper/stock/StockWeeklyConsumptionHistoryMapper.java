package com.economato.inventory.application.mapper.stock;

import com.economato.inventory.application.dto.shared.response.WeeklyConsumptionResponseDTO;
import com.economato.inventory.domain.model.stock.StockWeeklyConsumptionHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockWeeklyConsumptionHistoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unit", source = "product.unit")
    WeeklyConsumptionResponseDTO toDTO(StockWeeklyConsumptionHistory entity);
}
