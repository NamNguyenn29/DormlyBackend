package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.StudentProfileRequest;
import com.example.DormlyBackend.dto.response.StudentProfileResponseDto;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.service.StudentProfileService;
import com.example.DormlyBackend.service.UserDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    @PutMapping("/student-profile")
    public ApiResponse<StudentProfileResponseDto> upsert(@RequestBody StudentProfileRequest request) {
        UUID userId = currentUserId();
        StudentProfileResponseDto result = studentProfileService.upsert(userId, request);
        return ApiResponse.<StudentProfileResponseDto>builder()
                .message("Student profile upsert successfully")
                .result(result)
                .build();
    }

    @GetMapping("/student-profile")
    public ApiResponse<StudentProfileResponseDto> get() {
        UUID userId = currentUserId();
        StudentProfileResponseDto result = studentProfileService.getByUserId(userId);
        return ApiResponse.<StudentProfileResponseDto>builder()
                .message("Get student profile successfully")
                .result(result)
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof com.example.DormlyBackend.configuration.security.UserPrincipal up) {
            return up.getId();
        }
        throw new IllegalStateException("Unsupported principal type: " + principal);
    }
}
