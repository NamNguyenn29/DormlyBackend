package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.RoleRequest;
import com.example.DormlyBackend.dto.response.RoleResponseDto;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "audit.createdAt", target = "createdAt")
    @Mapping(source = "audit.createdBy", target = "createdBy")
    @Mapping(source = "audit.updatedAt", target = "updatedAt")
    @Mapping(source = "audit.updatedBy", target = "updatedBy")
    @Mapping(source = "permissions", target = "permissions")
    RoleResponseDto toDto(Role role);

    void updateRoleFromRequest(@MappingTarget Role requestTarget, RoleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    Role toEntity(RoleRequest request);

    default Set<String> map(Set<Permission> permissions) {
        if (permissions == null)
            return null;
        return permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    default UUID map(String value) {
        return value != null ? UUID.fromString(value) : null;
    }
}
