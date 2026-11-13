package com.example.DormlyBackend.dto.response;

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
public class TicketMetricRecordDto {
    private UUID ticketId;
    private String ticketCode;
    private UUID cycleId;
    private UUID closureId;
    private Instant createdAt;
    private Instant openedAt;
    private Instant respondedAt;
    private Instant resolvedAt;
    private Instant closedAt;
    private Instant baselineDueAt;
    private Double calculatedValue;
    private boolean included;
    private String exclusionReason;
    private boolean breached;
}
