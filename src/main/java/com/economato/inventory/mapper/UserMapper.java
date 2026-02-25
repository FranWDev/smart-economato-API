package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.projection.UserProjection;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.model.User;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    UserResponseDTO toResponseDTO(User user);

    @Mapping(source = "isFirstLogin", target = "firstLogin")
    @Mapping(source = "isHidden", target = "hidden")
    UserResponseDTO toResponseDTO(UserProjection projection);

    @Named("teacherIdToUser")
    default User teacherIdToUser(Integer teacherId) {
        if (teacherId == null) {
            return null;
        }
        User teacher = new User();
        teacher.setId(teacherId);
        return teacher;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "firstLogin", ignore = true)
    @Mapping(target = "hidden", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "inventoryMovements", ignore = true)
    @Mapping(source = "teacherId", target = "teacher", qualifiedByName = "teacherIdToUser")
    User toEntity(UserRequestDTO requestDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "firstLogin", ignore = true)
    @Mapping(target = "hidden", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "inventoryMovements", ignore = true)
    @Mapping(source = "teacherId", target = "teacher", qualifiedByName = "teacherIdToUser")
    void updateEntity(UserRequestDTO requestDTO, @MappingTarget User user);
}
