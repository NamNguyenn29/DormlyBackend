package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.RoleRequest;
import com.example.DormlyBackend.dto.response.RoleResponseDto;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponseDto>> create(@RequestBody @Valid RoleRequest request) {
        var result = roleService.create(request);
        return ResponseEntity.ok(ApiResponse.<RoleResponseDto>builder().result(result).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponseDto>> getById(@PathVariable UUID id) {
        var result = roleService.getById(id);
        return ResponseEntity.ok(ApiResponse.<RoleResponseDto>builder().result(result).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid RoleRequest request) {
        var result = roleService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<RoleResponseDto>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponseDto>>> list() {
        var result = roleService.list();
        return ResponseEntity.ok(ApiResponse.<List<RoleResponseDto>>builder().result(result).build());
    }
}
