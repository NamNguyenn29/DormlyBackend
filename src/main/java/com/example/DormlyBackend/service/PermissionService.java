package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.PermissionRequest;
import com.example.DormlyBackend.dto.response.PermissionResponseDto;
import com.example.DormlyBackend.entity.authentication.Navigation;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.PermissionMapper;
import com.example.DormlyBackend.repository.NavigationRepository;
import com.example.DormlyBackend.repository.PermissionRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final NavigationRepository navigationRepository;
    private final PermissionMapper permissionMapper;

    public PermissionResponseDto create(PermissionRequest request) {
        String code = request.getResource()+"_"+request.getAction();
        permissionRepository.findByCode(code)
                .ifPresent(p -> {
                    throw ExceptionFactory.business(com.example.DormlyBackend.exception.code.ErrorCode.INVALID_REQUEST,
                            "Permission already exists: " + code);
                });

        Permission permission = permissionMapper.toEntity(request);
        permission.setCode(code);
        permission.setRoles(resolveRoles(request.getRoleIds()));
        permission.setNavigations(resolveNavigations(request.getNavigationIds()));
        permission = permissionRepository.save(permission);
        return permissionMapper.toDto(permission);
    }

    @Transactional(readOnly = true)
    public PermissionResponseDto getById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Permission", id));
        return permissionMapper.toDto(permission);
    }

    public PermissionResponseDto update(UUID id, PermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Permission", id));

        permissionMapper.updatePermissionFromRequest(permission, request);
        if(request.getRoleIds() != null) {
            permission.setRoles(resolveRoles(request.getRoleIds()));
        }
        if(request.getNavigationIds() != null) {
            permission.setNavigations(resolveNavigations(request.getNavigationIds()));
        }
        permission = permissionRepository.save(permission);
        return permissionMapper.toDto(permission);
    }

    public void delete(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Permission", id));
        permissionRepository.delete(permission);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponseDto> list() {
        return permissionRepository.findAll().stream().map(permissionMapper::toDto).toList();
    }

    private Set<Role> resolveRoles(Set<String> roleIds) {
        if (roleIds == null)
            return Set.of();
        return roleIds.stream()
                .map(this::toUuidOrThrow)
                .map(uuid -> roleRepository.findById(uuid)
                        .orElseThrow(() -> ExceptionFactory.notFound(
                                com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND,
                                "Role",
                                uuid)))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<Navigation> resolveNavigations(Set<String> navigationIds) {
        if (navigationIds == null)
            return new HashSet<>();
        return navigationIds.stream()
                .map(this::toUuidOrThrow)
                .map(uuid -> navigationRepository.findById(uuid)
                        .orElseThrow(() -> ExceptionFactory.notFound(
                                com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND,
                                "Navigation",
                                uuid)))
                .collect(java.util.stream.Collectors.toSet());
    }

    private UUID toUuidOrThrow(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw ExceptionFactory
                    .validation(List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "uuid", "Invalid UUID", value)));
        }
    }
}
