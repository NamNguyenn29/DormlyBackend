package com.example.DormlyBackend.policy.ticket;

import com.example.DormlyBackend.enums.TicketStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TicketAuthorizationContext(
        TicketStatus status,
        UUID reporterId,
        Set<UUID> effectiveAssigneeIds,
        boolean rawAssigneesEmpty,
        Instant now,
        Instant closedAt,
        boolean closureTimestampTrusted,
        boolean closureRequestExists,
        boolean pendingReopenRequest
) {}
