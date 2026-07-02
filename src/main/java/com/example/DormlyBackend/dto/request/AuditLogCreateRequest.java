package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AuditLogCreateRequest {

    private UUID userId;

    @NotBlank
    private String action;

    @NotBlank
    private String entityType;

    private String entityId;

    private String oldValues;

    private String newValues;
}
