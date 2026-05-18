package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.UserRequest;
import com.example.DormlyBackend.dto.response.UserResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "audit.createdAt", target = "createdAt")
    @Mapping(source = "audit.createdBy", target = "createdBy")
    @Mapping(source = "audit.updatedAt", target = "updatedAt")
    @Mapping(source = "audit.updatedBy", target = "updatedBy")
    UserResponseDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateUser(@MappingTarget User user, UserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "active", ignore = true)
    User toEntity(UserRequest request);

    default String map(UUID value) {
        return value != null ? value.toString() : null;
    }

    default UUID map(String value) {
        return value != null ? UUID.fromString(value) : null;
    }

    // Map roles to role names for UserResponseDto.roles
    default Set<String> mapRolesToNames(Set<Role> roles) {
        if (roles == null) {
            return null;
        }

        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    // Map role names (Set<String>) from requests to role entities (Set<Role>)
    // This is needed because UserRequest.roles -> User.roles
    default Set<Role> mapRoleNamesToRoles(Set<String> roleNames) {
        if (roleNames == null) {
            return null;
        }

        return roleNames.stream()
                .map(roleName -> {
                    Role role = new Role();
                    role.setName(roleName);
                    return role;
                })
                .collect(Collectors.toSet());
    }

}
