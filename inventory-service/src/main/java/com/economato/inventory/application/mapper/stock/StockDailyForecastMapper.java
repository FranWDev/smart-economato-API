package com.economato.inventory.application.mapper.stock;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.economato.inventory.application.dto.stock.response.DailyForecastResponseDTO;
import com.economato.inventory.domain.model.stock.StockDailyForecast;

@Mapper(componentModel = "spring")
public interface StockDailyForecastMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unit", source = "product.unit")
    @Mapping(target = "activeBatches", ignore = true)
    DailyForecastResponseDTO toDTO(StockDailyForecast entity);
}
