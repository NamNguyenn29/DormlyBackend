package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.PermissionAction;
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
public class PermissionRequest {

    @NotBlank
    String resource;

    PermissionAction action;

//    @NotBlank
//    String code;

    // UUID strings
    Set<String> roleIds;

    // UUID strings (optional)
    Set<String> navigationIds;
}
