package com.economato.inventory.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.economato.inventory.application.dto.response.StockLedgerResponseDTO;
import com.economato.inventory.domain.model.StockLedger;

@Mapper(componentModel = "spring")
public interface StockLedgerMapper {

    StockLedgerMapper INSTANCE = Mappers.getMapper(StockLedgerMapper.class);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "userName", source = "user.name")
    @Mapping(target = "blockNumber", source = "block.blockNumber")
    @Mapping(target = "blockHash", source = "block.blockHash")
    StockLedgerResponseDTO toDTO(StockLedger stockLedger);

}
