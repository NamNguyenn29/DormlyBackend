package com.example.DormlyBackend.dto.response;

import com.example.DormlyBackend.enums.TicketEventVisibility;
import com.example.DormlyBackend.enums.TicketStatus;
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
public class TicketHistoryResponseDto {
    private UUID id;
    private UUID ticketId;
    private UUID cycleId;
    private long sequence;
    private String type;
    private UUID actorId;
    private String actorName;
    private Instant occurredAt;
    private TicketEventVisibility visibility;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private String note;
    private String payload;
    private String commandId;
}
