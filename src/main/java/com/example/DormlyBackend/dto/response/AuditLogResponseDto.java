package com.example.DormlyBackend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class AuditLogResponseDto {

    private UUID id;

    private UUID userId;

    private String action;

    private String entityType;

    private String entityId;

    private String oldValues;

    private String newValues;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createdAt;
}
