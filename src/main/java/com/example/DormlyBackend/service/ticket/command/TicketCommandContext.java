package com.example.DormlyBackend.service.ticket.command;

import java.util.UUID;

public record TicketCommandContext(
        UUID actorId,
        long expectedVersion,
        String idempotencyKey
) {}
