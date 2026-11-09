package com.example.DormlyBackend.service.ticket.migration;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class ActivationVerification {

    private final boolean successful;
    private final int activeCyclesCreated;
    private final int resolvedCyclesCreated;
    private final int closureAnchorsCreated;
    private final int unmigratedCount;
    @Builder.Default
    private final List<String> discrepancies = Collections.emptyList();
}
