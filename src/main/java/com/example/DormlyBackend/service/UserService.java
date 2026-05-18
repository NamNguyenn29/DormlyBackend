package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.UserRequest;
import com.example.DormlyBackend.dto.response.UserResponseDto;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.UserMapper;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
        if(request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRoles(resolveRolesByName(request.getRoles()));
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

    private Set<Role> resolveRolesByName(Set<String> roles) {
        if (roles == null)
            return Set.of();

        return roles.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Role", name)))
                .collect(java.util.stream.Collectors.toSet());
    }
}
