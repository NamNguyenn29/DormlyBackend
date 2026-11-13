package com.example.DormlyBackend.dto.request;

import com.example.DormlyBackend.enums.ReopenDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReopenReviewRequest {

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    @NotNull(message = "decision is required")
    private ReopenDecision decision;

    private String reason;

    private Set<UUID> assigneeIds;
}
