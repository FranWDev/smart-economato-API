package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.DailyForecastResponseDTO;
import com.economato.inventory.domain.model.StockDailyForecast;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockDailyForecastMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unit", source = "product.unit")
    DailyForecastResponseDTO toDTO(StockDailyForecast entity);
}
