package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.Gender;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingNodeResponseDto {

    UUID id;

    UUID parentId;

    UUID nodeTypeId;

    String name;

    String description;

    Long maxCapacity;

    Long currentOccupancy;

    Gender genderPolicy;

    String status;

    // Recursive (used only by tree endpoint)
    Set<BuildingNodeResponseDto> children;

    // audit (optional)
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdBy;
    String updatedBy;
}
