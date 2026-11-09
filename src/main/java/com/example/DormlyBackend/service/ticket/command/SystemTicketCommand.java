package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.policy.ticket.TicketOperation;

import java.time.Instant;
import java.util.UUID;

public record SystemTicketCommand(
        UUID ticketId,
        UUID cycleId,
        long expectedVersion,
        TicketOperation operation,
        String deterministicKey,
        Instant now
) {}
