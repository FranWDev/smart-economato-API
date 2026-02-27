package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.economato.inventory.dto.response.StockLedgerResponseDTO;
import com.economato.inventory.model.StockLedger;

@Mapper(componentModel = "spring")
public interface StockLedgerMapper {

    StockLedgerMapper INSTANCE = Mappers.getMapper(StockLedgerMapper.class);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "userName", source = "user.name")
    StockLedgerResponseDTO toDTO(StockLedger stockLedger);

}
