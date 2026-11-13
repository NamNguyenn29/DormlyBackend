package com.example.DormlyBackend.service.ticket.command;

import java.util.List;
import java.util.UUID;

public record TicketMutationResult(
        List<TicketEventDraft> events,
        UUID reopenRequestId
) {}
