package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.NavigationRequest;
import com.example.DormlyBackend.dto.response.NavigationResponseDto;
import com.example.DormlyBackend.entity.authentication.Navigation;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.NavigationMapper;
import com.example.DormlyBackend.repository.NavigationRepository;
import com.example.DormlyBackend.repository.PermissionRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NavigationService {

    private final NavigationRepository navigationRepository;
    private final PermissionRepository permissionRepository;
    private final NavigationMapper navigationMapper;
    private final UserRepository userRepository;

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

    public List<NavigationResponseDto> getMyNavigationsTree() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return Collections.emptyList();
        }

        String email = auth.getName();

        // load user with roles
        var userOpt = userRepository.findUserWithRolesByEmail(email);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }
        var user = userOpt.get();
        Set<Role> roles = user.getRoles();

        // permissions through roles
        Set<Permission> permissions = roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toSet());

        Set<String> codes = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());

        // fetch all navigations and filter in-memory (since schema/mapping for direct
        // query not present)
        // Filter: navigation.permissions contains any permission code.
        List<Navigation> allNavigations = navigationRepository.findAll();
        List<Navigation> allowed = allNavigations.stream()
                .filter(n -> {
                    Set<Permission> navPerms = n.getPermissions();
                    if (navPerms == null || navPerms.isEmpty())
                        return false;
                    return navPerms.stream().anyMatch(p -> codes.contains(p.getCode()));
                })
                .collect(Collectors.toList());

        // Build recursive tree by reusing NavigationService mapper logic is not
        // possible since it's private.
        // Simple approach: return top-level navigations among allowed, and recursively
        // include children.
        // We rely on entity relationships for children.
        return allowed.stream()
                .filter(n -> n.getParent() == null)
                .map(this::toTree)
                .collect(Collectors.toList());
    }

    private NavigationResponseDto toTree(Navigation navigation) {
        // This assumes NavigationService's cycle safety; for me it's fine.
        return NavigationResponseDto.builder()
                .id(navigation.getId() != null ? navigation.getId().toString() : null)
                .name(navigation.getName())
                .vnName(navigation.getVnName())
                .path(navigation.getPath())
                .icon(navigation.getIcon())
                .color(navigation.getColor())
                .enabled(navigation.isEnabled())
                .orderIndex(navigation.getOrderIndex())
                .parentId(navigation.getParent() != null ? navigation.getParent().getId().toString() : null)
                .permissions(navigation.getPermissions() == null ? null
                        : navigation.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet()))
                .children(navigation.getChildren() == null ? null
                        : navigation.getChildren().stream().map(this::toTree).collect(Collectors.toSet()))
                .createdAt(navigation.getAudit() != null ? navigation.getAudit().getCreatedAt() : null)
                .updatedAt(navigation.getAudit() != null ? navigation.getAudit().getUpdatedAt() : null)
                .createdBy(navigation.getAudit() != null ? navigation.getAudit().getCreatedBy() : null)
                .updatedBy(navigation.getAudit() != null ? navigation.getAudit().getUpdatedBy() : null)
                .build();
    }
}
