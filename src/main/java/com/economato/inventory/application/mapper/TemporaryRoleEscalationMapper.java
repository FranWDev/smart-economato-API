package com.economato.inventory.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.economato.inventory.application.dto.request.RoleEscalationRequestDTO;
import com.economato.inventory.domain.model.TemporaryRoleEscalation;
import com.economato.inventory.domain.model.User;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface TemporaryRoleEscalationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "expirationTime", source = "requestDTO", qualifiedByName = "calculateExpirationTime")
    TemporaryRoleEscalation toEntity(RoleEscalationRequestDTO requestDTO, User user);

    @Named("calculateExpirationTime")
    default LocalDateTime calculateExpirationTime(RoleEscalationRequestDTO requestDTO) {
        if (requestDTO == null || requestDTO.getDurationMinutes() == null) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(requestDTO.getDurationMinutes());
    }
}
