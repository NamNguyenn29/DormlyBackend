package com.example.DormlyBackend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssigneesUpdateRequest {

    /** Full replacement of the assignee set. An empty set unassigns everyone. */
    @Builder.Default
    private Set<UUID> userIds = new LinkedHashSet<>();

    private Long expectedVersion;

    private String reason;
}
