package com.economato.inventory.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.economato.inventory.application.dto.response.DailyForecastResponseDTO;
import com.economato.inventory.domain.model.StockDailyForecast;

@Mapper(componentModel = "spring")
public interface StockDailyForecastMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unit", source = "product.unit")
    @Mapping(target = "activeBatches", ignore = true)
    DailyForecastResponseDTO toDTO(StockDailyForecast entity);
}
