package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.UserDocumentsRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.UserDocumentResponseDto;
import com.example.DormlyBackend.dto.request.AdminDocumentStatusRequest;
import com.example.DormlyBackend.service.UserDocumentService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserDocumentController {

    private final UserDocumentService userDocumentService;

    @PostMapping(value = "/documents", consumes = { "multipart/form-data" })
    public ApiResponse<UserDocumentResponseDto> upsert(
            @RequestPart("documentType") String documentType,
            @RequestPart("status") String status,
            @RequestPart(value = "rejectReason", required = false) String rejectReason,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        UUID userId = currentUserId();
        UserDocumentResponseDto result = userDocumentService.upsert(userId, documentType, status, rejectReason, file);

        return ApiResponse.<UserDocumentResponseDto>builder()
                .message("User documents upsert successfully")
                .result(result)
                .build();
    }

    @GetMapping("/documents")
    public ApiResponse<List<UserDocumentResponseDto>> list() {

        UUID userId = currentUserId();

        List<UserDocumentResponseDto> result = userDocumentService.listByUserId(userId);
        return ApiResponse.<List<UserDocumentResponseDto>>builder()
                .message("List user documents successfully")
                .result(result)
                .build();
    }

    @PatchMapping("/documents/{documentId}/status")
    public ApiResponse<UserDocumentResponseDto> setStatus(
            @PathVariable UUID documentId,
            @RequestBody AdminDocumentStatusRequest request) {
        UserDocumentResponseDto result = userDocumentService.setDocumentStatus(
                documentId,
                request.getStatus() != null ? request.getStatus().name() : null,
                request.getRejectReason());

        return ApiResponse.<UserDocumentResponseDto>builder()
                .message("Document status updated successfully")
                .result(result)
                .build();
    }

    @GetMapping("/documents/grouped-by-user-id")
    public ApiResponse<Map<UUID, List<UserDocumentResponseDto>>> listGroupedByUserId() {
        var result = userDocumentService.listAllGroupedByUserId();
        return ApiResponse.<Map<UUID, List<UserDocumentResponseDto>>>builder()

                .message("List documents grouped by userId successfully")
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
