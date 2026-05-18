package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavigationRequest {

    @NotBlank(message = "Name cannot be blank")
    String name;

    String vnName;

    @NotBlank(message = "Path cannot be blank")
    String path;

    String icon;
    String color;

    boolean enabled;

    Integer orderIndex;

    // UUID string (nullable)
    String parentId;

    // UUID strings
    Set<String> permissionIds;
}
