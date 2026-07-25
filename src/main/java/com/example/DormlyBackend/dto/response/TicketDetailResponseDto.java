package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailResponseDto {
    private UUID id;
    private String code;
    private String title;
    private String description;
    private TicketCategory category;
    private TicketStatus status;
    private TicketPriority priority;
    private LocalDate dueDate;
    private UUID reporterId;
    private String reporterName;
    private UUID buildingNodeId;
    private String buildingNodeName;
    private List<TicketAssigneeResponseDto> assignees;
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private List<TicketAttachmentResponseDto> attachments;
    private List<TicketCommentResponseDto> comments;
    private LocalDateTime createdAt;
}
