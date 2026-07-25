package com.example.DormlyBackend.dto.request;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class TicketAssigneesUpdateRequest {

    /** Full replacement of the assignee set. An empty set unassigns everyone. */
    private Set<UUID> userIds = new LinkedHashSet<>();
}
