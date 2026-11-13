package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.entity.ticket.Ticket;

import java.time.Instant;

@FunctionalInterface
public interface TicketSystemMutation {
    TicketMutationResult apply(Ticket ticket, Instant now);
}
