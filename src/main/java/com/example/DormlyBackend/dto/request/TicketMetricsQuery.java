package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketDataOrigin;
import com.example.DormlyBackend.enums.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMetricsQuery {
    private Instant from;
    private Instant to;
    private TicketCategory category;
    private TicketPriority priority;
    private UUID buildingNodeId;
    private UUID assigneeId;
    @Builder.Default
    private String cohort = "LIVE";
    @Builder.Default
    private TicketDataOrigin dataOrigin = TicketDataOrigin.LIVE;
}
