package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.NavigationRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.NavigationResponseDto;
import com.example.DormlyBackend.service.NavigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/navigations")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;

    @PostMapping
    public ResponseEntity<ApiResponse<NavigationResponseDto>> create(@RequestBody @Valid NavigationRequest request) {
        var result = navigationService.create(request);
        return ResponseEntity.ok(ApiResponse.<NavigationResponseDto>builder()
                .message("Navigation create successfully")
                .result(result)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NavigationResponseDto>> getById(@PathVariable UUID id) {
        var result = navigationService.getById(id);
        return ResponseEntity.ok(ApiResponse.<NavigationResponseDto>builder()
                .message("Navigation get by id successfully")
                .result(result)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NavigationResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid NavigationRequest request) {
        var result = navigationService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<NavigationResponseDto>builder()
                .message("Navigation update successfully")
                .result(result)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        navigationService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Navigation delete successfully")
                .result(null)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NavigationResponseDto>>> list() {
        var result = navigationService.list();
        return ResponseEntity.ok(ApiResponse.<List<NavigationResponseDto>>builder()
                .message("Navigation list successfully")
                .result(result)
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NavigationResponseDto>>> me() {
        var result = navigationService.getMyNavigationsTree();
        return ResponseEntity.ok(ApiResponse.<List<NavigationResponseDto>>builder()
                .message("Navigation me successfully")
                .result(result)
                .build());
    }
}
