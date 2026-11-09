package com.example.DormlyBackend.policy.ticket;

import java.time.Instant;

public record SlaDeadlines(Instant responseDueAt, Instant resolutionDueAt) {}
