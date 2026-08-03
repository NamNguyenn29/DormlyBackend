package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.AnnouncementRequestDto;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.AnnouncementResponseDto;
import com.example.DormlyBackend.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ApiResponse<List<AnnouncementResponseDto>> getAll() {
        var result = announcementService.getAll();
        return ApiResponse.<List<AnnouncementResponseDto>>builder()
                .result(result)
                .message("Get announcements successfully")
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AnnouncementResponseDto> getById(@PathVariable UUID id) {
        var result = announcementService.getById(id);
        return ApiResponse.<AnnouncementResponseDto>builder()
                .result(result)
                .message("Get announcement successfully")
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<AnnouncementResponseDto> create(@RequestBody @Valid AnnouncementRequestDto request) {
        var result = announcementService.create(request);
        return ApiResponse.<AnnouncementResponseDto>builder()
                .result(result)
                .message("Announcement created successfully")
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ApiResponse.<Void>builder()
                .message("Announcement deleted successfully")
                .build();
    }
}
