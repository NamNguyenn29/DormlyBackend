package com.example.DormlyBackend.service.ticket.migration;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class MigrationPreflight {

    private final boolean ready;
    private final int totalLegacyTickets;
    private final int eligibleTickets;
    @Builder.Default
    private final List<UUID> blockerTicketIds = Collections.emptyList();
    @Builder.Default
    private final Map<UUID, String> assigneeErrors = Collections.emptyMap();
    @Builder.Default
    private final List<UUID> missingTimezoneTicketIds = Collections.emptyList();
    @Builder.Default
    private final List<UUID> missingDataTicketIds = Collections.emptyList();
    @Builder.Default
    private final List<String> warnings = Collections.emptyList();

    public boolean hasBlockers() {
        return blockerTicketIds != null && !blockerTicketIds.isEmpty();
    }
}
