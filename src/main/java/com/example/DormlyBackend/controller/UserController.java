package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.UserRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.UserResponseDto;
import com.example.DormlyBackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponseDto> create(@RequestBody @Valid UserRequest request) {
        var result = userService.create(request);
        return ApiResponse.<UserResponseDto>builder().message("User create successfully").result(result).build();

    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponseDto> getById(@PathVariable UUID id) {
        var result = userService.getById(id);
        return ApiResponse.<UserResponseDto>builder().result(result).message("Get user by id successfully").build();
    }


    @PutMapping("/{id}")
    public ApiResponse<UserResponseDto> update(@PathVariable UUID id,
            @RequestBody @Valid UserRequest request) {
        var result = userService.update(id, request);
        return ApiResponse.<UserResponseDto>builder().result(result).message("Update user successfully").build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ApiResponse.<Void>builder().result(null).build();
    }

    @PreAuthorize("hasRole('AMIN') or hasAuthority('USERMANAGEMENT_READ')")
    @GetMapping
    public ApiResponse<List<UserResponseDto>> list() {
        var result = userService.list();
        return ApiResponse.<List<UserResponseDto>>builder().result(result).message("Get users successfully").build();
    }
}
