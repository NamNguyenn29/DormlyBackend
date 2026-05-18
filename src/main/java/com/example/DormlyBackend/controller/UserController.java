package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.UserRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.UserResponseDto;
import com.example.DormlyBackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDto>> create(@RequestBody @Valid UserRequest request) {
        var result = userService.create(request);
        return ResponseEntity.ok(ApiResponse.<UserResponseDto>builder().result(result).build());

    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> getById(@PathVariable UUID id) {
        var result = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.<UserResponseDto>builder().result(result).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid UserRequest request) {
        var result = userService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<UserResponseDto>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponseDto>>> list() {
        var result = userService.list();
        return ResponseEntity.ok(ApiResponse.<List<UserResponseDto>>builder().result(result).build());
    }
}
