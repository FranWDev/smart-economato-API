package com.economato.inventory.application.mapper.notification;

import com.economato.inventory.application.dto.notification.response.NotificationResponseDTO;
import com.economato.inventory.domain.model.notification.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "read", target = "isRead")
    @Mapping(source = "sender.name", target = "senderName")
    NotificationResponseDTO toResponseDTO(Notification notification);
}
