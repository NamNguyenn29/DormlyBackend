package com.example.DormlyBackend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class RoomHistoryResponseDto {

    UUID roomAssignmentId;

    UUID roomNodeId;

    LocalDateTime startDate;

    LocalDateTime endDate;

    String assignedBy;

    String contractUrl;

    String notes;
}
