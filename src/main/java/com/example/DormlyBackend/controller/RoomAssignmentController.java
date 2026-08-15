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

    @PostMapping("/move-out")
    public ResponseEntity<ApiResponse<Void>> moveOut(@RequestParam UUID userId) {
        roomAssignmentService.moveOutUser(userId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().message("Move out executed successfully").build());
    }

    @PostMapping("/assign-auto")
    public ResponseEntity<ApiResponse<RoomAssignmentResponseDto>> assignAuto(
            @RequestParam UUID userId,
            @RequestParam(required = false) String assignedBy,
            @RequestParam(required = false) String contractUrl,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        LocalDateTime parsedStart = parseDateTime(startDate);
        LocalDateTime parsedEnd = parseDateTime(endDate);

        var result = roomAssignmentService.assignAuto(userId, parsedStart, parsedEnd, assignedBy, contractUrl, notes);
        return ResponseEntity.ok(ApiResponse.<RoomAssignmentResponseDto>builder().result(result).build());
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception e1) {
            try {
                return java.time.ZonedDateTime.parse(raw).toLocalDateTime();
            } catch (Exception e2) {
                try {
                    return java.time.LocalDate.parse(raw).atStartOfDay();
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }
}
