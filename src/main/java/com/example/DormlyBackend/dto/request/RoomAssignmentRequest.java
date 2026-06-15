package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomAssignmentRequest {

    UUID userId;

    UUID roomNodeId;

    @NotNull
    LocalDateTime startDate;

    LocalDateTime endDate;

    String assignedBy;

    String contractUrl;

    String notes;

    // For CRUD update we keep fields optional; validation is applied mainly on
    // create/assign.
}
