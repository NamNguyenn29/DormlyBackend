package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TicketReopenStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReopenResponseDto {
    private UUID id;
    private UUID ticketId;
    private UUID closureEventId;
    private UUID requesterId;
    private String requesterName;
    private String reason;
    private Instant requestedAt;
    private TicketReopenStatus status;
    private UUID reviewerId;
    private String reviewerName;
    private Instant reviewedAt;
    private String decisionReason;
}
