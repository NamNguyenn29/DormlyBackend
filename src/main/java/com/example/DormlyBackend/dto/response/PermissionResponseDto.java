package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.PermissionAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponseDto {
    String id;

    String resource;
    PermissionAction action;
    String code;

    Set<String> roles;
    Set<String> navigations;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdBy;
    String updatedBy;
}
