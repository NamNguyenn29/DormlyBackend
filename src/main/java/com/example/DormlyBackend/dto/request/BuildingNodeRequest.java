package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingNodeRequest {

    @NotBlank(message = "Name cannot be blank")
    String name;

    @Size(max = 5000, message = "Description is too long")
    String description;

    Long maxCapacity;

    Long currentOccupancy;

    // Stored as Gender enum in entity (nullable)
    Gender genderPolicy;

    String status;

    @NotBlank(message = "nodeTypeId cannot be blank")
    String nodeTypeId;

    // UUID string (nullable)
    String parentId;
}
