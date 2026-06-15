package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.configuration.AuditMetaData;
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
public class RoomAssignmentResponseDto {

    UUID id;

    UUID userId;

    UUID roomNodeId;

    LocalDateTime startDate;

    LocalDateTime endDate;

    String assignedBy;

    String contractUrl;

    String notes;

    AuditMetaData auditMetaData;
}
