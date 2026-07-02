package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.AuditLogCreateRequest;
import com.example.DormlyBackend.dto.response.AuditLogResponseDto;
import com.example.DormlyBackend.entity.audit.AuditLog;
import com.example.DormlyBackend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogResponseDto create(
            AuditLogCreateRequest request,
            HttpServletRequest httpServletRequest) {
        String ip = extractIp(httpServletRequest);
        String ua = httpServletRequest != null ? httpServletRequest.getHeader("User-Agent") : null;

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setUserId(request.getUserId());
        entity.setAction(request.getAction());
        entity.setEntityType(request.getEntityType());
        entity.setEntityId(request.getEntityId());
        entity.setOldValues(request.getOldValues());
        entity.setNewValues(request.getNewValues());
        entity.setIpAddress(ip);
        entity.setUserAgent(ua);
        entity.setCreatedAt(LocalDateTime.now());

        AuditLog saved = auditLogRepository.save(entity);

        return AuditLogResponseDto.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .action(saved.getAction())
                .entityType(saved.getEntityType())
                .entityId(saved.getEntityId())
                .oldValues(saved.getOldValues())
                .newValues(saved.getNewValues())
                .ipAddress(saved.getIpAddress())
                .userAgent(saved.getUserAgent())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public Page<AuditLogResponseDto> search(
            UUID userId,
            String action,
            String entityType,
            String entityId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        return auditLogRepository.search(userId, action, entityType, entityId, from, to, pageable)
                .map(a -> AuditLogResponseDto.builder()
                        .id(a.getId())
                        .userId(a.getUserId())
                        .action(a.getAction())
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .oldValues(a.getOldValues())
                        .newValues(a.getNewValues())
                        .ipAddress(a.getIpAddress())
                        .userAgent(a.getUserAgent())
                        .createdAt(a.getCreatedAt())
                        .build());
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // In case of multiple IPs: client, proxy1, proxy2...
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
