package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.NavigationRequest;
import com.example.DormlyBackend.dto.response.NavigationResponseDto;
import com.example.DormlyBackend.entity.authentication.Navigation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface NavigationMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "audit.createdAt", target = "createdAt")
    @Mapping(source = "audit.createdBy", target = "createdBy")
    @Mapping(source = "audit.updatedAt", target = "updatedAt")
    @Mapping(source = "audit.updatedBy", target = "updatedBy")
    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "permissions", target = "permissions")
    @Mapping(source = "children", target = "children")
    NavigationResponseDto toDto(Navigation navigation);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    Navigation toEntity(NavigationRequest request);

    void updateNavigationFromRequest(@MappingTarget Navigation navigation, NavigationRequest request);

    default Set<String> map(Set<com.example.DormlyBackend.entity.authentication.Permission> permissions) {
        if (permissions == null)
            return null;
        return permissions.stream().map(com.example.DormlyBackend.entity.authentication.Permission::getCode)
                .collect(Collectors.toSet());
    }

    default UUID map(String value) {
        return value != null ? UUID.fromString(value) : null;
    }

    // Cycle-safety will be handled in service by controlling children mapping
    // depth.
    default Set<NavigationResponseDto> mapChildren(Set<Navigation> children) {
        if (children == null)
            return null;
        return children.stream().map(this::toDto).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
