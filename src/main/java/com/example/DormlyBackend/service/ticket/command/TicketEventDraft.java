package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.enums.TicketEventVisibility;
import com.example.DormlyBackend.enums.TicketStatus;

import java.util.UUID;

public record TicketEventDraft(
        String type,
        TicketEventVisibility visibility,
        UUID cycleId,
        UUID actorId,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String note,
        String payload
) {}
