package com.example.DormlyBackend.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeTypeResponseDto {

    UUID id;

    String name;

    int level;

    // audit fields (optional)
    java.time.LocalDateTime createdAt;
    java.time.LocalDateTime updatedAt;
    String createdBy;
    String updatedBy;
}
