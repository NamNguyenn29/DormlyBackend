package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.RoomAssignmentRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.RoomAssignmentResponseDto;
import com.example.DormlyBackend.service.RoomAssignmentService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room-assignments")
@RequiredArgsConstructor
public class RoomAssignmentController {

    private final RoomAssignmentService roomAssignmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> create(
            @RequestBody @Valid RoomAssignmentRequest request) {
        var result = roomAssignmentService.create(request);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> getById(@PathVariable UUID id) {
        var result = roomAssignmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid RoomAssignmentRequest request) {
        var result = roomAssignmentService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roomAssignmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomAssignmentResponseDto>>> list() {
        var result = roomAssignmentService.list();
        return ResponseEntity.ok(ApiResponse.<List<RoomAssignmentResponseDto>>builder().result(result).build());
    }

    @PostMapping("/assign-manual")
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> assignManual(
            @RequestBody @Valid RoomAssignmentRequest request) {
        var result = roomAssignmentService.assignManual(request);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }

    @PostMapping("/assign-auto")
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> assignAuto(
            @RequestParam UUID userId,
            @RequestParam(required = false) String assignedBy,
            @RequestParam(required = false) String contractUrl,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        var result = roomAssignmentService.assignAuto(userId, startDate, endDate, assignedBy, contractUrl, notes);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }
}
