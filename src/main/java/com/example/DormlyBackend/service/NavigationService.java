package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.NavigationRequest;
import com.example.DormlyBackend.dto.response.NavigationResponseDto;
import com.example.DormlyBackend.entity.authentication.Navigation;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.NavigationMapper;
import com.example.DormlyBackend.repository.NavigationRepository;
import com.example.DormlyBackend.repository.PermissionRepository;
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
public class NavigationService {

    private final NavigationRepository navigationRepository;
    private final PermissionRepository permissionRepository;
    private final NavigationMapper navigationMapper;

    public NavigationResponseDto create(NavigationRequest request) {
        Navigation navigation = navigationMapper.toEntity(request);
        if (request.getParentId() != null) {
            navigation.setParent(navigationRepository.findById(UUID.fromString(request.getParentId()))
                    .orElseThrow(() -> ExceptionFactory.notFound(
                            com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Navigation",
                            request.getParentId())));
        }
        navigation.setPermissions(resolvePermissions(request.getPermissionIds()));

        // children are derived from parent relationships
        navigation = navigationRepository.save(navigation);
        return toResponseRecursive(navigation);
    }

    @Transactional(readOnly = true)
    public NavigationResponseDto getById(UUID id) {
        Navigation navigation = navigationRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Navigation", id));
        return toResponseRecursive(navigation);
    }

    public NavigationResponseDto update(UUID id, NavigationRequest request) {
        Navigation navigation = navigationRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Navigation", id));

        navigationMapper.updateNavigationFromRequest(navigation, request);

        if (request.getParentId() != null) {
            navigation.setParent(navigationRepository.findById(UUID.fromString(request.getParentId()))
                    .orElseThrow(() -> ExceptionFactory.notFound(
                            com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Navigation",
                            request.getParentId())));
        } else {
            navigation.setParent(null);
        }

        navigation.setPermissions(resolvePermissions(request.getPermissionIds()));
        navigation = navigationRepository.save(navigation);
        return toResponseRecursive(navigation);
    }

    public void delete(UUID id) {
        Navigation navigation = navigationRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND, "Navigation", id));
        navigationRepository.delete(navigation);
    }

    @Transactional(readOnly = true)
    public List<NavigationResponseDto> list() {
        return navigationRepository.findAll().stream().map(this::toResponseRecursive).toList();
    }

    private Set<Permission> resolvePermissions(Set<String> permissionIds) {
        if (permissionIds == null)
            return Set.of();
        return permissionIds.stream()
                .map(UUID::fromString)
                .map(uuid -> permissionRepository.findById(uuid)
                        .orElseThrow(() -> ExceptionFactory.notFound(
                                com.example.DormlyBackend.exception.code.ErrorCode.RESOURCE_NOT_FOUND,
                                "Permission",
                                uuid)))
                .collect(java.util.stream.Collectors.toSet());
    }

    // Cycle-safe recursive conversion: do not revisit the same node
    private NavigationResponseDto toResponseRecursive(Navigation navigation) {
        return toResponseRecursive(navigation, new HashSet<>());
    }

    private NavigationResponseDto toResponseRecursive(Navigation navigation, Set<UUID> visited) {
        if (navigation == null)
            return null;
        if (!visited.add(navigation.getId())) {
            // Break potential cycles
            return NavigationResponseDto.builder().id(String.valueOf(navigation.getId())).build();
        }

        NavigationResponseDto dto = navigationMapper.toDto(navigation);

        // Build children recursively from entity relationship
        Set<NavigationResponseDto> childrenDtos = new HashSet<>();
        if (navigation.getChildren() != null) {
            for (Navigation child : navigation.getChildren()) {
                childrenDtos.add(toResponseRecursive(child, visited));
            }
        }
        dto.setChildren(childrenDtos);
        return dto;
    }
}
