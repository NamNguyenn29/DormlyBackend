package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketPriority;

import java.time.Duration;

public record SlaPolicySnapshot(
        String version,
        TicketPriority priority,
        Duration response,
        Duration resolution
) {}
