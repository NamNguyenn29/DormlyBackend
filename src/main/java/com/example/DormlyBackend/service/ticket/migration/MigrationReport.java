package com.example.DormlyBackend.service.ticket.migration;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MigrationReport {

    private final UUID runId;
    private final Instant cutoverAt;
    private final String status; // "COMPLETED", "IN_PROGRESS", "FAILED"
    private final int processedCount;
    private final int remainingCount;
    private final int failureCount;
    private final String checkpoint;
    @Builder.Default
    private final List<String> failureDetails = Collections.emptyList();
}
