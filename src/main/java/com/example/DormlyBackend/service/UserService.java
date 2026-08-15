package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.ChangePasswordRequest;
import com.example.DormlyBackend.dto.request.UserRequest;
import com.example.DormlyBackend.dto.response.UserResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.UserMapper;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.configuration.aop.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserResponseDto create(UserRequest request) {
        userRepository.findUserWithRolesByEmail(request.getEmail())
                .ifPresent(u -> {
                    throw ExceptionFactory.business(ErrorCode.USER_ALREADY_EXISTS, request.getEmail());
                });

        User user = userMapper.toEntity(request);
        user.setActive(false);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(resolveRolesByName(request.getRoles()));
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.id.toString() == authentication.principal.id.toString()")
    @Transactional(readOnly = true)
    public UserResponseDto getById(UUID id) {
        User user = userRepository.findUserWithRoles(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, id));
        return userMapper.toDto(user);
    }

    public UserResponseDto update(UUID id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "User", id));

        userMapper.updateUser(user, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRoles() != null) {
            user.setRoles(resolveRolesByName(request.getRoles()));
        }
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    public void delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, id));
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> list() {
        return userRepository.findAll().stream().map(userMapper::toDto).toList();
    }

    public UserResponseDto toggleStatus(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, id));
        user.setActive(!user.isActive());
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Audit(action = "UPDATE_PASSWORD", entityType = "USER", entityId = "#userId")
    public void updatePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, userId));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw ExceptionFactory.business(ErrorCode.PASSWORD_NOT_EQUAL, request.getConfirmPassword());
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw ExceptionFactory.business(ErrorCode.WRONG_PASSWORD, request.getConfirmPassword());
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private Set<Role> resolveRolesByName(Set<String> roles) {
        if (roles == null)
            return Set.of();

        return roles.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseGet(() -> {
                            Role newRole = new Role();
                            newRole.setName(name);
                            return roleRepository.save(newRole);
                        }))
                .collect(java.util.stream.Collectors.toSet());
    }

}
