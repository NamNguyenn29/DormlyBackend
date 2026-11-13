package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.*;
import com.example.DormlyBackend.policy.ticket.TicketAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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

    // Phase 3 fields
    private Long version;
    private Set<TicketAction> allowedActions;
    private Instant phase3EnabledAt;
    private Instant responseStartedAt;
    private Instant firstResponseAt;
    private Instant responseDueAt;
    private Instant baselineResponseDueAt;
    private Instant responseBreachedAt;
    private TicketResponseOutcome responseOutcome;
    private Integer cycleNo;
    private Instant resolutionDueAt;
    private Instant baselineResolutionDueAt;
    private Instant resolutionBreachedAt;
    private Instant firstResolvedAt;
    private Instant latestResolvedAt;
    private Instant confirmationDueAt;
    private Instant confirmationRemindedAt;
    private TicketCloseReason closeReason;
    private Instant reopenEligibleUntil;
    private Boolean pendingReopenRequest;
    private Boolean needsReassignment;
    private String overdueSource;
    private TicketReopenResponseDto reopenRequest;
    private Boolean replayed;
}
