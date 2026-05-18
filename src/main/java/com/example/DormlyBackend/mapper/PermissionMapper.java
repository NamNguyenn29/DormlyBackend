package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.PermissionRequest;
import com.example.DormlyBackend.dto.response.PermissionResponseDto;
import com.example.DormlyBackend.entity.authentication.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "audit.createdAt", target = "createdAt")
    @Mapping(source = "audit.createdBy", target = "createdBy")
    @Mapping(source = "audit.updatedAt", target = "updatedAt")
    @Mapping(source = "audit.updatedBy", target = "updatedBy")
    @Mapping(source = "roles", target = "roles")
    @Mapping(source = "navigations", target = "navigations")
    PermissionResponseDto toDto(Permission permission);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "navigations", ignore = true)
    Permission toEntity(PermissionRequest request);

    void updatePermissionFromRequest(@MappingTarget  Permission permission, PermissionRequest request);

    default Set<String> mapRoles(Set<com.example.DormlyBackend.entity.authentication.Role> roles) {
        if (roles == null)
            return null;
        return roles.stream().map(com.example.DormlyBackend.entity.authentication.Role::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapNavigations(Set<com.example.DormlyBackend.entity.authentication.Navigation> navigations) {
        if (navigations == null)
            return null;
        return navigations.stream().map(com.example.DormlyBackend.entity.authentication.Navigation::getName)
                .collect(Collectors.toSet());
    }

    default UUID map(String value) {
        return value != null ? UUID.fromString(value) : null;
    }
}
