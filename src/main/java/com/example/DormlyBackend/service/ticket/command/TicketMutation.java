package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.policy.ticket.TicketActor;

import java.time.Instant;

@FunctionalInterface
public interface TicketMutation {
    TicketMutationResult apply(Ticket ticket, TicketActor actor, Instant now);
}
