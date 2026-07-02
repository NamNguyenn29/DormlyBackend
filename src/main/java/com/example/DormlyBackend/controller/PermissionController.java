package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.PermissionRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.PermissionResponseDto;
import com.example.DormlyBackend.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponseDto>> create(@RequestBody @Valid PermissionRequest request) {
        var result = permissionService.create(request);
        return ResponseEntity.ok(ApiResponse.<PermissionResponseDto>builder()
                .message("Permission create successfully")
                .result(result)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> getById(@PathVariable UUID id) {
        var result = permissionService.getById(id);
        return ResponseEntity.ok(ApiResponse.<PermissionResponseDto>builder()
                .message("Permission get by id successfully")
                .result(result)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid PermissionRequest request) {
        var result = permissionService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<PermissionResponseDto>builder()
                .message("Permission update successfully")
                .result(result)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionService.delete(id);
        return ResponseEntity
                .ok(ApiResponse.<Void>builder().message("Permission delete successfully").result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> list() {
        var result = permissionService.list();
        return ResponseEntity.ok(ApiResponse.<List<PermissionResponseDto>>builder()
                .message("Permission list successfully")
                .result(result)
                .build());
    }
}
