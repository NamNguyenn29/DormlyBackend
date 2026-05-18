package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.response.NavigationResponseDto;
import com.example.DormlyBackend.entity.authentication.Navigation;
import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.repository.NavigationRepository;
import com.example.DormlyBackend.repository.PermissionRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NavigationMeService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final NavigationRepository navigationRepository;

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
