package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.NotificationResponseDTO;
import com.economato.inventory.domain.model.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "read", target = "isRead")
    @Mapping(source = "sender.name", target = "senderName")
    NotificationResponseDTO toResponseDTO(Notification notification);
}
