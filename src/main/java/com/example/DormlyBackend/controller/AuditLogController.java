package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.AuditLogCreateRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.AuditLogResponseDto;
import com.example.DormlyBackend.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    public ApiResponse<AuditLogResponseDto> create(
            @RequestBody @Valid AuditLogCreateRequest request,
            HttpServletRequest httpServletRequest) {
        AuditLogResponseDto result = auditLogService.create(request, httpServletRequest);
        return ApiResponse.<AuditLogResponseDto>builder()
                .message("Audit log created successfully")
                .result(result)
                .build();
    }

    @GetMapping
    public ApiResponse<Page<AuditLogResponseDto>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        Page<AuditLogResponseDto> result = auditLogService.search(userId, action, entityType, entityId, from, to,
                pageable);
        return ApiResponse.<Page<AuditLogResponseDto>>builder()
                .message("Audit logs fetched successfully")
                .result(result)
                .build();
    }
}
