package com.example.DormlyBackend.dto.response;

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
public class NavigationResponseDto {
    String id;

    String name;
    String vnName;
    String path;

    String icon;
    String color;

    boolean enabled;
    Integer orderIndex;

    // UUID string (nullable)
    String parentId;

    Set<String> permissions;

    // Recursive
    Set<NavigationResponseDto> children;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdBy;
    String updatedBy;
}
