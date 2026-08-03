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

import com.example.DormlyBackend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

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

        return mapToDto(saved);
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
                .map(this::mapToDto);
    }

    private AuditLogResponseDto mapToDto(AuditLog saved) {
        String email = null;
        String fullName = null;
        if (saved.getUserId() != null) {
            var userOpt = userRepository.findById(saved.getUserId());
            if (userOpt.isPresent()) {
                email = userOpt.get().getEmail();
                fullName = userOpt.get().getFullName();
            }
        }

        return AuditLogResponseDto.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .userEmail(email)
                .userFullName(fullName)
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

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // In case of multiple IPs: client, proxy1, proxy2...
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
