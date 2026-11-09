package com.example.DormlyBackend.service.ticket.command;

import java.util.UUID;

public record TicketCommandResult(
        UUID ticketId,
        UUID reopenRequestId,
        long version,
        boolean replayed
) {}
