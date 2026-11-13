package com.example.DormlyBackend.policy.ticket;

import java.util.UUID;

public record TicketActor(UUID id, boolean admin, boolean staff, boolean active) {}
