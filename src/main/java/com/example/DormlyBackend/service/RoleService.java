package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.RoleRequest;
import com.example.DormlyBackend.dto.response.RoleResponseDto;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.RoleMapper;
import com.example.DormlyBackend.repository.PermissionRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    public RoleResponseDto create(RoleRequest request) {
        roleRepository.findByName(request.getName())
                .ifPresent(r -> {
                    throw ExceptionFactory.business(com.example.DormlyBackend.exception.code.ErrorCode.INVALID_REQUEST,
                            "Role already exists: " + request.getName());
                });

        Role role = roleMapper.toEntity(request);
        role.setPermissions(resolvePermissions(request.getPermissionIds()));
        role = roleRepository.save(role);
        return roleMapper.toDto(role);
    }

    @Transactional(readOnly = true)
    public RoleResponseDto getById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory
                        .notFound(com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Role", id));
        return roleMapper.toDto(role);
    }

    public RoleResponseDto update(UUID id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory
                        .notFound(com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Role", id));

        roleMapper.updateRoleFromRequest(role, request);
        role.setPermissions(resolvePermissions(request.getPermissionIds()));
        role = roleRepository.save(role);
        return roleMapper.toDto(role);
    }

    public void delete(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory
                        .notFound(com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Role", id));
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDto> list() {
        return roleRepository.findAll().stream().map(roleMapper::toDto).toList();
    }

    private Set<Permission> resolvePermissions(Set<String> permissionIds) {
        if (permissionIds == null)
            return Set.of();
        return permissionIds.stream()
                .map(this::toUuidOrThrow)
                .map(uuid -> permissionRepository.findById(uuid)
                        .orElseThrow(() -> ExceptionFactory.notFound(
                                com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND,
                                "Permission",
                                uuid)))
                .collect(java.util.stream.Collectors.toSet());
    }

    private UUID toUuidOrThrow(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw ExceptionFactory
                    .validation(List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            "permissionIds", "Invalid UUID", value)));
        }
    }
}
